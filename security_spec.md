# Firestore Security Specification

## 1. Data Invariants
1. **Shop Ownership**: A `ShopWorkspace` document at `/shops/{shopId}` is strictly owned by a single verified Gmail user (`shopId == request.auth.uid && ownerUid == request.auth.uid`). No other user can read, list, create, update, or delete it.
2. **Master Gate SyncRecord Isolation**: Every `SyncRecord` at `/shops/{shopId}/records/{recordId}` must have a valid parent `/shops/{shopId}` owned by `request.auth.uid`, and `ownerUid == request.auth.uid` on the record itself.
3. **Strict Schema & Immutability**: Only declared keys (`hasAll` + `hasOnly`) are permitted. `ownerUid` and `serverCreatedAt` are immutable after creation. Server timestamps must match `request.time`.
4. **Verified Gmail Enforcement**: All write operations require `request.auth.token.email_verified == true`.
5. **No Blanket List / No Client Trust**: `allow list` on `/shops` and `/shops/{shopId}/records` strictly checks `resource.data.ownerUid == request.auth.uid` without calling `get()`.

## 2. Adversarial Test Payloads ("Dirty Dozen")
1. **Unauthenticated Read**: `get(/shops/uid1)` without `request.auth` -> `PERMISSION_DENIED`.
2. **Cross-User Read**: Signed in as `uid2`, `get(/shops/uid1/records/rec1)` -> `PERMISSION_DENIED`.
3. **Blanket Collection Scrape**: Signed in as `uid2`, `list(/shops)` without `where('ownerUid', '==', 'uid2')` -> `PERMISSION_DENIED`.
4. **Identity Spoofing on Create**: Signed in as `uid2`, `create(/shops/uid2, { ownerUid: 'uid1', ... })` -> `PERMISSION_DENIED`.
5. **Unverified Email Spoof**: Signed in with `email_verified: false`, `create(/shops/uid1, ...)` -> `PERMISSION_DENIED`.
6. **Shadow Field Injection**: `create(/shops/uid1/records/rec1, { ..., isAdmin: true })` -> `PERMISSION_DENIED`.
7. **Oversized ID Poisoning**: `create(/shops/uid1/records/${'a'.repeat(200)}, ...)` -> `PERMISSION_DENIED`.
8. **Invalid Record ID Characters**: `create(/shops/uid1/records/bad$id, ...)` -> `PERMISSION_DENIED`.
9. **Client Timestamp Forgery**: `create(/shops/uid1, { ..., serverCreatedAt: pastTimestamp })` -> `PERMISSION_DENIED`.
10. **Immutable Owner Tampering on Update**: `update(/shops/uid1/records/rec1, { ownerUid: 'uid2' })` -> `PERMISSION_DENIED`.
11. **Disallowed CollectionKey Injection**: `create(/shops/uid1/records/rec1, { collectionKey: 'admin_backdoor', ... })` -> `PERMISSION_DENIED`.
12. **Orphan Record Write Without Parent Shop**: `create(/shops/uid1/records/rec1, ...)` when `/shops/uid1` does not exist -> `PERMISSION_DENIED`.
