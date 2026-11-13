import { InventoryMeta, Product, StockHistoryEntry } from '../types';
import { nowDateTime } from '../utils/format';
import { readJson, readString, writeJson, writeString } from './businessStorage';

const PROD_NS = 'tamanna_enterprise_products';
const KEY_PRODUCTS = 'products';
const KEY_NEXT_CODE = 'next_product_code';
const FIRST_PRODUCT_NUMBER = 228622;

export const ProductStorage = {
  getProducts(): Product[] {
    return readJson<Product[]>(PROD_NS, KEY_PRODUCTS, []);
  },

  nextProductCode(): string {
    const rawNext = Number(readString(PROD_NS, KEY_NEXT_CODE, '0'));
    let next = Number.isFinite(rawNext) ? Math.floor(rawNext) : 0;
    if (next < FIRST_PRODUCT_NUMBER) {
      const maxExisting = this.getProducts().reduce((max, p) => {
        const m = /^P-(\d+)$/i.exec(p.code.trim());
        const n = m ? parseInt(m[1], 10) : 0;
        return n > max ? n : max;
      }, 0);
      next = Math.max(FIRST_PRODUCT_NUMBER, maxExisting + 1);
    }
    return 'P-' + String(next).padStart(6, '0');
  },

  addProduct(product: Omit<Product, 'createdAt' | 'updatedAt'> & Partial<Pick<Product, 'createdAt' | 'updatedAt'>>): boolean {
    const normalizedCode = product.code.trim().toUpperCase();
    const match = /^P-(\d{6})$/.exec(normalizedCode);
    const codeNumber = match ? parseInt(match[1], 10) : 0;
    if (codeNumber < FIRST_PRODUCT_NUMBER) return false;
    if (
      !product.name.trim() ||
      !Number.isFinite(product.purchasePrice) ||
      product.purchasePrice < 0 ||
      !Number.isFinite(product.salePrice) ||
      product.salePrice < 0 ||
      !Number.isFinite(product.stockQuantity) ||
      product.stockQuantity < 0
    ) {
      return false;
    }

    const products = this.getProducts();
    if (products.some((p) => p.code.toUpperCase() === normalizedCode)) return false;

    const now = Date.now();
    const created: Product = {
      code: normalizedCode,
      name: product.name.trim(),
      purchasePrice: product.purchasePrice,
      salePrice: product.salePrice,
      stockQuantity: Math.floor(product.stockQuantity),
      createdAt: product.createdAt && product.createdAt > 0 ? product.createdAt : now,
      updatedAt: now,
    };

    const saved = writeJson(PROD_NS, KEY_PRODUCTS, [...products, created]);
    if (!saved) return false;

    const currentNext = Number(readString(PROD_NS, KEY_NEXT_CODE, String(FIRST_PRODUCT_NUMBER))) || FIRST_PRODUCT_NUMBER;
    if (codeNumber >= currentNext) {
      writeString(PROD_NS, KEY_NEXT_CODE, String(Math.max(FIRST_PRODUCT_NUMBER, codeNumber + 1)));
    }
    return true;
  },

  updateProduct(product: Product): boolean {
    if (
      !product.name.trim() ||
      !Number.isFinite(product.purchasePrice) ||
      product.purchasePrice < 0 ||
      !Number.isFinite(product.salePrice) ||
      product.salePrice < 0 ||
      !Number.isFinite(product.stockQuantity) ||
      product.stockQuantity < 0
    ) {
      return false;
    }
    const products = this.getProducts();
    let found = false;
    const updated = products.map((p) => {
      if (p.code.toUpperCase() === product.code.trim().toUpperCase()) {
        found = true;
        return {
          ...product,
          code: p.code,
          name: product.name.trim(),
          stockQuantity: Math.floor(product.stockQuantity),
          createdAt: p.createdAt,
          updatedAt: Date.now(),
        };
      }
      return p;
    });
    if (!found) return false;
    return writeJson(PROD_NS, KEY_PRODUCTS, updated);
  },

  updateStock(code: string, newStock: number): boolean {
    if (!Number.isFinite(newStock) || newStock < 0) return false;
    const products = this.getProducts();
    let found = false;
    const updated = products.map((p) => {
      if (p.code.toUpperCase() === code.trim().toUpperCase()) {
        found = true;
        return { ...p, stockQuantity: Math.floor(newStock), updatedAt: Date.now() };
      }
      return p;
    });
    if (!found) return false;
    return writeJson(PROD_NS, KEY_PRODUCTS, updated);
  },

  deleteProduct(code: string): boolean {
    const products = this.getProducts();
    const updated = products.filter((p) => p.code.toUpperCase() !== code.trim().toUpperCase());
    if (updated.length === products.length) return false;
    return writeJson(PROD_NS, KEY_PRODUCTS, updated);
  },
};

const META_NS = 'tamanna_inventory_meta';
const KEY_META = 'meta';
const KEY_HISTORY = 'history';

export const InventoryMetaStorage = {
  getAllMeta(): InventoryMeta[] {
    return readJson<InventoryMeta[]>(META_NS, KEY_META, []);
  },

  getMeta(code: string): InventoryMeta {
    return (
      this.getAllMeta().find((m) => m.code.toUpperCase() === code.trim().toUpperCase()) ?? {
        code,
        batch: '',
        expiryDate: '',
        lowStockLimit: 5,
        damagedQuantity: 0,
      }
    );
  },

  saveMeta(meta: InventoryMeta): boolean {
    const all = this.getAllMeta().filter((m) => m.code.toUpperCase() !== meta.code.trim().toUpperCase());
    all.push({
      ...meta,
      lowStockLimit: Math.max(0, Math.floor(meta.lowStockLimit)),
      damagedQuantity: Math.max(0, Math.floor(meta.damagedQuantity)),
    });
    return writeJson(META_NS, KEY_META, all);
  },

  getHistory(): StockHistoryEntry[] {
    const list = readJson<StockHistoryEntry[]>(META_NS, KEY_HISTORY, []);
    return [...list].sort((a, b) => b.id - a.id);
  },

  addHistory(
    code: string,
    productName: string,
    type: string,
    quantity: number,
    before: number,
    after: number,
    note = ''
  ): boolean {
    const history = this.getHistory();
    const used = new Set(history.map((h) => h.id));
    let id = Date.now();
    while (used.has(id)) id++;
    const entry: StockHistoryEntry = {
      id,
      date: nowDateTime(),
      code,
      productName,
      type,
      quantity,
      before,
      after,
      note,
    };
    const next = [entry, ...history].slice(0, 500);
    return writeJson(META_NS, KEY_HISTORY, next);
  },
};
