# TANI — خطة الصيانة والتحديث الشامل

## الهدف

تحويل مشروع **تاني | TANI** من نسخة قابلة للبناء والاختبار الأولي إلى منتج **Production-Ready** حديث، آمن، قابل للصيانة والتوسع، بدون إعادة كتابة المشروع بالكامل من الصفر.

الخطة تعتمد على:
- إصلاح المشاكل الحالية أولاً.
- تقوية الأمان والمنطق التجاري.
- إعادة تنظيم بنية الكود تدريجياً.
- تحديث التصميم وتجربة الاستخدام.
- بناء نظام اختبارات وCI/CD فعلي.
- تجهيز التطبيق للتوسع المستقبلي في الدفع، التوصيل، المدن، الذكاء الاصطناعي، والويب/iOS.

---

# 1. الأولويات العامة

| الأولوية | المجال | الهدف |
|---|---|---|
| P0 | Security & Auth | إزالة أخطر المخاطر الأمنية |
| P1 | Architecture & Commerce | إصلاح بنية الكود ومنطق الطلبات |
| P2 | Testing | حماية المشروع من regressions |
| P3 | UI/UX Modernization | تحديث تجربة الاستخدام والتصميم |
| P4 | CI/CD & Release | إنشاء pipeline إنتاج حقيقي |
| P5 | Performance & Offline | تحسين الأداء والشبكات الضعيفة |
| P6 | Monitoring & Operations | مراقبة الأخطاء والأداء |
| P7 | Future Platform | تجهيز البنية للتوسع المستقبلي |

---

# 2. Phase 0 — تثبيت النسخة الحالية

## المطلوب

- اعتماد آخر نسخة ناجحة على `main` كنقطة مرجعية.
- إنشاء branch مخصص للصيانة:
  - `maintenance/v2`
- إنشاء Tag للإصدار الحالي:
  - `v1.0.0-baseline`
- حفظ نسخة من:
  - Supabase schema.
  - RLS policies.
  - RPCs.
  - Storage policies.
  - إعدادات Auth.
- منع إضافة Features جديدة أثناء إصلاح الأساس.
- إنشاء ملف:
  - `CURRENT_KNOWN_ISSUES.md`
- إضافة changelog رسمي.

## النتيجة

نسخة مستقرة يمكن الرجوع إليها في أي وقت أثناء إعادة الهيكلة.

---

# 3. Phase 1 — Security Hardening

هذه المرحلة يجب تنفيذها قبل أي تطوير كبير.

## 3.1 حماية جلسة المستخدم

### المشكلة الحالية

Access Token وRefresh Token محفوظان محلياً بصورة غير كافية للحماية.

### التعديل

إنشاء:

```text
SessionManager
SecureTokenStorage
AuthRepository
```

استخدام:

```text
Android Keystore
+
Encrypted local storage
```

وعدم وضع التوكنات في SharedPreferences عادي.

### المطلوب أيضاً

- حذف التوكنات عند Logout.
- التعامل مع refresh token rotation.
- session expiration handling.
- منع race conditions أثناء refresh.
- اختبار expired access token.
- اختبار revoked refresh token.
- اختبار offline refresh failure.

---

## 3.2 DataStore

استبدال SharedPreferences العادية بـ:

```text
Jetpack DataStore
```

للبيانات مثل:

- الإعدادات.
- onboarding state.
- notification preferences.
- آخر مدينة.
- إعدادات العرض.

عدم استخدام DataStore لتخزين بيانات حساسة بدون طبقة تشفير مناسبة.

---

## 3.3 Password Reset

### إلغاء

```text
tani://auth/reset
```

### استبداله بـ

```text
https://<TANI_DOMAIN>/auth/reset
```

مع:

```text
Android Verified App Links
autoVerify=true
assetlinks.json
```

### الهدف

منع تطبيق آخر من اعتراض رابط استعادة كلمة المرور.

---

## 3.4 Supabase Security Audit

مراجعة جميع:

```text
Tables
Views
RLS Policies
RPC Functions
SECURITY DEFINER Functions
Storage Buckets
Storage Policies
GRANT / REVOKE
Triggers
```

### القواعد

- لا توجد public tables بدون RLS.
- لا توجد privileged function متاحة لـ `anon`.
- كل RPC حساس يتحقق من:
  - `auth.uid()`
  - role
  - ownership
  - account status
- تحديد `search_path` صراحة.
- تقليل SECURITY DEFINER لأقل حد.
- إضافة indexes للأعمدة المستخدمة في RLS.
- منع direct writes عندما يجب استخدام RPC.

---

## 3.5 Supabase Auth

تفعيل حيثما أمكن:

```text
Leaked Password Protection
Password minimum strength
Email verification
Rate limits
Session controls
```

ومراجعة:

- Signup abuse.
- Password reset abuse.
- brute force protection.
- account enumeration.

---

## 3.6 Admin Security

لوحة الإدارة تحتاج:

- الاعتماد على server-side authorization دائماً.
- عدم اعتبار إخفاء زر في الواجهة حماية.
- كل Admin RPC يتحقق من role داخل PostgreSQL.
- فصل:
  - admin
  - support
- إضافة audit log لكل إجراء حساس.
- تسجيل:
  - من نفذ الإجراء.
  - وقت التنفيذ.
  - الهدف.
  - السبب.
  - القيمة القديمة والجديدة عند الحاجة.

---

# 4. Phase 2 — Architecture Refactor

## المشكلة

`Repository.kt` و`Supabase.kt` يحملان مسؤوليات كثيرة جداً.

## الهدف

اعتماد بنية حديثة قابلة للاختبار والتوسع.

## الهيكل المقترح

```text
app/

core/
    network/
    auth/
    security/
    database/
    analytics/
    monitoring/
    ui/

data/
    auth/
    marketplace/
    cart/
    orders/
    merchant/
    profile/

domain/
    model/
    repository/
    usecase/

feature/
    auth/
    home/
    categories/
    search/
    product/
    cart/
    checkout/
    orders/
    profile/
    merchant/
    support/
```

---

## 4.1 تقسيم Repository

بدلاً من Repository واحد:

```text
AuthRepository
ProfileRepository
MarketplaceRepository
ProductRepository
CartRepository
OrderRepository
MerchantRepository
SupportRepository
GrowthRepository
MonetizationRepository
```

---

## 4.2 Use Cases

إنشاء حالات استخدام مستقلة مثل:

```text
LoginUseCase
SignupUseCase
GetHomeFeedUseCase
SearchProductsUseCase
AddToCartUseCase
ValidateCartUseCase
CheckoutUseCase
CancelOrderUseCase
SubmitMerchantApplicationUseCase
ReviewProductUseCase
```

---

## 4.3 ViewModels

كل شاشة تعتمد على:

```text
Fragment / Compose
        ↓
ViewModel
        ↓
UseCase
        ↓
Repository Interface
        ↓
Repository Implementation
        ↓
Supabase / Cache
```

لا يجب أن تتصل الشاشة بـSupabase مباشرة.

---

## 4.4 Network Layer

إنشاء طبقة موحدة:

```text
ApiClient
AuthInterceptor
ErrorMapper
RetryPolicy
NetworkMonitor
```

مع توحيد:

- timeout.
- retries.
- parsing.
- error messages.
- unauthorized handling.
- token refresh.

---

# 5. Phase 3 — Commerce Logic Hardening

هذه المرحلة أساسية لأن الأخطاء هنا تؤثر على المال والطلبات.

---

## 5.1 Cart

### الحالي

السلة تحفظ Product كامل محلياً.

### الجديد

السلة المحلية تخزن فقط:

```text
productId
variantId
quantity
addedAt
```

ثم يتم جلب الحالة الحالية من الخادم.

---

## 5.2 Cart Quote

إنشاء RPC مثل:

```text
quote_cart()
```

يرجع:

```text
Current price
Current stock
Product availability
Merchant status
Delivery fee
Discounts
Subtotal
Grand total
Validation warnings
```

الـAndroid يعرض Quote من السيرفر قبل تأكيد الطلب.

---

## 5.3 Server Authority

لا يعتمد الخادم على القيم القادمة من Android في:

```text
Price
Stock
Delivery fee
Discount
Commission
Subscription entitlement
Featured status
Order totals
```

السيرفر يحسبها دائماً.

---

## 5.4 Checkout

المسار النهائي:

```text
Cart
↓
Validate
↓
Server Quote
↓
Customer Confirm
↓
Atomic Checkout RPC
↓
Order Group
↓
Merchant Orders
↓
Inventory Update
↓
Status History
```

---

## 5.5 Idempotency

الحفاظ على idempotency key.

إضافة اختبارات:

- double-click checkout.
- retry after timeout.
- network reconnect.
- duplicated API request.
- concurrent checkout.

---

## 5.6 Inventory

يجب منع:

```text
stock < 0
```

باستخدام transaction-level locking أو atomic update داخل PostgreSQL.

اختبار:

```text
آخر قطعة
+
عميلان
+
Checkout في نفس اللحظة
```

يجب أن ينجح طلب واحد فقط.

---

## 5.7 Order State Machine

تعريف الحالات رسمياً:

```text
pending
accepted
preparing
ready
out_for_delivery
delivered
cancelled
rejected
failed
```

وتحديد transitions المسموحة.

مثال:

```text
pending → accepted
pending → rejected
pending → cancelled

accepted → preparing
accepted → cancelled

preparing → ready

ready → out_for_delivery

out_for_delivery → delivered
```

أي انتقال غير مصرح به يرفضه الخادم.

---

## 5.8 Cancellation

تحديد:

- متى يستطيع العميل الإلغاء.
- متى يستطيع التاجر الإلغاء.
- متى يحتاج Admin.
- إعادة المخزون تلقائياً.
- تسجيل سبب الإلغاء.
- تحديث analytics.

---

## 5.9 Delivery

إنشاء abstraction:

```text
DeliveryProvider
```

حتى يدعم التطبيق مستقبلاً:

```text
Merchant delivery
Platform delivery
Third-party delivery
Pickup
```

---

## 5.10 Payments

إنشاء abstraction:

```text
PaymentProvider
```

والحالة الحالية:

```text
CashOnDeliveryProvider
```

لاحقاً يمكن إضافة:

```text
Bank payment
Mobile wallet
Payment gateway
```

بدون تعديل Order Engine بالكامل.

---

# 6. Phase 4 — Automated Testing

المشروع لا يجب أن يعتمد على Build success فقط.

## 6.1 Unit Tests

تغطية:

```text
Password validation
Phone validation
Search normalization
Cart calculations
Order transitions
Delivery rules
Pricing rules
Merchant eligibility
```

---

## 6.2 Repository Tests

اختبار:

```text
AuthRepository
CartRepository
OrderRepository
MerchantRepository
```

باستخدام fake/mock APIs.

---

## 6.3 Database Tests

اختبار Supabase/PostgreSQL:

```text
RLS
RPC permissions
Order creation
Inventory locking
Cancellation
Merchant ownership
Admin permissions
Support permissions
Account deletion
```

---

## 6.4 Security Tests

اختبار محاولات:

```text
Customer → another customer data
Customer → merchant private data
Merchant → another merchant orders
Merchant → admin RPC
Anonymous → protected RPC
Support → admin-only action
```

كلها يجب أن تفشل.

---

## 6.5 UI Tests

المسارات الأساسية:

```text
Signup
Login
Password Reset
Home
Search
Product Details
Add to Cart
Checkout
Order Tracking
Merchant onboarding
Merchant order handling
```

---

## 6.6 End-to-End Tests

سيناريو كامل:

```text
Create customer
→ Browse
→ Cart
→ Checkout
→ Merchant accepts
→ Preparing
→ Delivery
→ Delivered
→ Review
```

---

# 7. Phase 5 — UI/UX Modernization

## الاستراتيجية

عدم إعادة كتابة كل التطبيق مرة واحدة.

استخدام migration تدريجي.

---

## 7.1 Design System

إنشاء:

```text
TaniTheme
TaniColors
TaniTypography
TaniSpacing
TaniShapes
TaniElevation
```

ومكونات:

```text
TaniButton
TaniCard
TaniTextField
TaniProductCard
TaniStoreCard
TaniBadge
TaniLoading
TaniEmptyState
TaniErrorState
```

---

## 7.2 Material 3

توحيد التطبيق على:

```text
Material 3
```

مع دعم:

- RTL.
- Arabic typography.
- dynamic font scaling.
- dark mode.
- accessibility.

---

## 7.3 Compose Migration

المسار المقترح:

```text
XML الحالي
↓
Design System
↓
Compose للشاشات الجديدة
↓
Home
↓
Search
↓
Product
↓
Cart
↓
Checkout
↓
Orders
↓
Profile
↓
Merchant
```

ثم الانتقال الكامل إلى Navigation Compose بعد انتهاء الهجرة.

---

## 7.4 Home Screen

استبدال:

```text
ScrollView + LinearLayouts
```

بـ:

```text
LazyColumn
```

مع:

```text
Search Bar
Banner
Categories LazyRow
Featured products
Recommended products
Newest products
Top-rated products
Stores
```

---

## 7.5 Products

استخدام:

```text
LazyVerticalGrid
```

أو RecyclerView أثناء مرحلة XML.

كل Product Card تعرض فقط أهم المعلومات:

```text
Image
Name
Price
Store
Rating
Stock state
Favorite
Featured label
```

---

## 7.6 Search

تطوير البحث ليشمل:

```text
Arabic normalization
Typo tolerance
Ranking
Recent searches
Suggestions
Filters
Sort
Categories
Price
Availability
Merchant
```

---

## 7.7 Product Details

إضافة:

```text
Image gallery
Variant selector
Stock state
Delivery information
Merchant trust
Ratings
Reviews
Related products
Sticky Add to Cart
```

---

## 7.8 Checkout UX

تقسيم واضح:

```text
Address
Merchant groups
Items
Delivery
Payment
Summary
Confirm
```

مع عرض:

```text
Product total
Delivery per merchant
Grand total
```

قبل التأكيد.

---

# 8. Phase 6 — Performance & Offline

تاني يجب أن يكون Low-Bandwidth First.

## المطلوب

```text
Paging
Caching
Image caching
Retry with exponential backoff
Offline states
Network monitoring
Timeout handling
Skeleton loaders
Image compression
```

---

## 8.1 Local Cache

استخدام Room عندما يكون هناك احتياج حقيقي لـ:

```text
Home cache
Products cache
Categories
Search results
Stores
Orders read cache
```

---

## 8.2 Images

إضافة image loading library مناسبة.

المطلوب:

- resize server/client side.
- memory cache.
- disk cache.
- placeholders.
- failure state.
- WebP/AVIF عند الملاءمة.

---

## 8.3 Paging

عدم تحميل عشرات أو مئات المنتجات مرة واحدة.

اعتماد:

```text
Paging 3
```

لـ:

```text
Products
Search
Stores
Orders
Reviews
Admin lists
```

---

## 8.4 Performance Tests

إضافة:

```text
Macrobenchmark
Baseline Profiles
```

للشاشات:

```text
Startup
Home
Search
Product
Cart
Checkout
```

---

# 9. Phase 7 — CI/CD

الـGitHub Actions الحالي يبني Debug فقط.

## Pipeline الجديد

```text
Checkout
↓
Dependency verification
↓
Static analysis
↓
Android Lint
↓
Unit tests
↓
Database tests
↓
Security tests
↓
Debug build
↓
UI tests
↓
Release build
↓
R8
↓
Signed AAB
↓
Artifacts
```

---

## 9.1 Branch Strategy

```text
main
develop
feature/*
fix/*
release/*
```

أو trunk-based إذا أصبح الفريق صغيراً وسريعاً.

---

## 9.2 Environments

فصل:

```text
Development
Staging
Production
```

لكل واحدة Supabase منفصل.

---

## 9.3 Production Secrets

عدم وضع الأسرار داخل:

```text
GitHub repository
Android source
Admin source
```

استخدام:

```text
GitHub Secrets
Supabase secrets
Environment variables
```

---

## 9.4 Release

تجهيز:

```text
Release signing
AAB
R8 / ProGuard
versioning
release notes
changelog
```

---

# 10. Phase 8 — Monitoring & Observability

## Crash Monitoring

تسجيل:

```text
Crash
ANR
Fatal errors
Network errors
Database errors
Checkout failures
```

---

## Analytics

الأحداث الأساسية:

```text
app_open
signup
login
search
product_view
add_to_cart
remove_from_cart
begin_checkout
checkout_success
checkout_failure
order_cancelled
order_delivered
review_created
repeat_purchase
```

---

## Merchant Analytics

```text
Product views
Orders
Conversion
GMV
Cancellation
Response time
Repeat customers
Best sellers
```

---

## Operational Alerts

إنشاء alerts عند:

```text
Checkout failure spike
Login failure spike
Order cancellation spike
Supabase error spike
Database latency
Crash increase
Merchant delivery failure
```

---

# 11. Phase 9 — Admin Platform Modernization

لوحة Admin الحالية يمكن الاحتفاظ بها مؤقتاً.

ثم تحويلها تدريجياً إلى تطبيق Web حديث.

## المقترح

```text
React / Next.js
+
Supabase
```

أو إطار ويب مناسب وقت التنفيذ.

## الوحدات

```text
Dashboard
Merchants
Products
Orders
Complaints
Support
Reviews
Featured
Subscriptions
Analytics
Audit logs
System configuration
```

---

# 12. Phase 10 — Future-Ready Platform

البنية يجب أن تدعم بدون إعادة كتابة:

```text
Electronic Payments
Delivery integrations
Coupons
Loyalty
Push notifications
AI recommendations
AI search
Image search
Voice search
Merchant CRM
Multiple cities
Web customer app
iOS
B2B
APIs
```

---

## Abstractions المطلوبة

```text
PaymentProvider
DeliveryProvider
NotificationProvider
SearchProvider
AnalyticsProvider
StorageProvider
RecommendationProvider
```

---

# 13. ترتيب التنفيذ النهائي

## P0 — أمان

1. Secure session storage.
2. Android Keystore.
3. Password reset App Links.
4. Supabase RLS audit.
5. RPC audit.
6. SECURITY DEFINER hardening.
7. Auth hardening.
8. Admin authorization audit.

---

## P1 — المنطق والبنية

1. تقسيم Repository.
2. تقسيم Supabase client.
3. إضافة ViewModels.
4. إضافة Use Cases.
5. Cart redesign.
6. Server quote.
7. Checkout hardening.
8. Inventory concurrency.
9. Order state machine.

---

## P2 — الاختبارات

1. Unit tests.
2. Repository tests.
3. Database tests.
4. RLS tests.
5. Checkout concurrency tests.
6. UI tests.
7. End-to-end tests.

---

## P3 — التصميم

1. Design System.
2. Material 3.
3. Navigation cleanup.
4. Compose migration.
5. Home redesign.
6. Product cards.
7. Search redesign.
8. Cart/Checkout redesign.
9. Accessibility.

---

## P4 — Release

1. Lint.
2. Tests in CI.
3. Staging environment.
4. Release signing.
5. R8.
6. AAB.
7. Release pipeline.

---

## P5 — الأداء

1. Room cache.
2. Paging.
3. Image caching.
4. Offline UX.
5. Retry strategy.
6. Baseline Profiles.
7. Macrobenchmark.

---

## P6 — التشغيل والتوسع

1. Monitoring.
2. Analytics.
3. Alerts.
4. Admin modernization.
5. Feature flags.
6. Remote config.
7. Payment abstraction.
8. Delivery abstraction.
9. AI/search expansion.

---

# 14. Definition of Done

لا تعتبر أي مرحلة مكتملة إلا إذا:

```text
Code implemented
+
Tests added
+
Tests passing
+
Security reviewed
+
Documentation updated
+
CI passing
+
No regression in existing flows
```

---

# 15. شروط الجاهزية للإطلاق Production

قبل الإطلاق يجب أن تكون:

- Critical security issues = 0.
- High security issues = 0.
- جميع RLS policies مختبرة.
- Checkout concurrency مختبر.
- Inventory race conditions مختبرة.
- Password reset آمن.
- Release build ناجح.
- Signed AAB جاهز.
- Crash monitoring يعمل.
- Analytics تعمل.
- Backup/restore strategy موجودة.
- Staging environment موجود.
- End-to-end purchase flow ناجح.
- تجربة المستخدم مختبرة على أجهزة حقيقية.
- اختبار شبكة ضعيفة ومنقطعة.
- سياسات Terms / Privacy / Merchant / Delivery / Cancellation متاحة.

---

# 16. النتيجة المستهدفة

بعد تنفيذ P0–P3:

> تاني يصبح قريباً من Production Grade بدلاً من مجرد تطبيق يعمل ويُبنى بنجاح.

بعد تنفيذ P4–P6:

> يصبح المشروع منصة مستقرة، قابلة للتوسع والصيانة لسنوات، ويمكن إضافة الدفع الإلكتروني والتوصيل والمدن الجديدة والميزات الذكية دون إعادة بناء الأساس في كل مرة.

---

**Document:** TANI Maintenance & Modernization Plan  
**Project:** TANI  
**Target:** Production-ready, secure, scalable marketplace platform
