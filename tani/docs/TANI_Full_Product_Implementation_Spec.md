# تاني | TANI
## وثيقة المنتج والاستراتيجية والأعمال والتقنية والتنفيذ — النسخة الكاملة

**نوع المشروع:** Multi-Vendor Marketplace + Merchant Enablement Platform  
**السوق الأول:** كوستي – السودان  
**الاسم التجاري:** تاني | TANI  
**الفئات الأولية:** الملابس، مستلزمات البيت، الأطفال، المنتجات المحلية والمشاريع المنزلية  
**النموذج:** Marketplace أولاً، ثم Merchant Tools وخدمات تجارية متقدمة تدريجياً  
**التوصيل:** التاجر مسؤول عن التوصيل في البداية، وفق سياسات المنصة  
**الدفع:** الدفع عند الاستلام، مع قابلية إضافة وسائل دفع إلكترونية لاحقاً  
**إدارة المنتجات:** التاجر يضيف منتجاته ويديرها بنفسه  
**تنفيذ الطلب:** كامل داخل التطبيق  
**الهدف:** بناء تجربة شراء محلية سهلة، موثوقة، ممتعة ومتكررة الاستخدام، مع قيمة واضحة وقابلة للقياس للتاجر.

**إصدار الملف:** tani v1  
**خط الأساس:** آخر نسخة من وثيقة المنتج + خطة الصيانة والتحديث الشامل  
**حالة الوثيقة:** Production-Readiness Specification  
**قاعدة الترقيم:** كل تحديث لاحق للملف يزيد الرقم تسلسلياً: tani v2، tani v3، ...

> **ملاحظة إصدار:** هذه الوثيقة تصف المنتج الكامل المستهدف لـ **تاني**. تم حذف جميع تصنيفات ومراحل ومصطلحات MVP، واستبدال اسم Waffer/وافر باسم تاني/TANI.

---

# 1. Executive Summary

**تاني** هو Marketplace محلي يبدأ من كوستي، يجمع منتجات التجار والبائعين الأونلاين وأصحاب المشاريع المنزلية والمتاجر الصغيرة في مكان واحد، ويتيح للعميل اكتشاف المنتجات، مقارنة الخيارات، الطلب والدفع عند الاستلام من خلال تجربة منظمة داخل التطبيق.

تاني ليس مجرد تطبيق يجمع المنتجات.

> **تاني منصة تجارة محلية تجعل اكتشاف المنتجات والشراء من البائعين المحليين أسهل وأكثر ثقة وتنظيماً، وتمنح البائع قناة بيع رقمية منظمة تساعده على الوصول للعملاء وإدارة الطلبات وتنمية تجارته.**

المنتج مصمم كمنظومة كاملة تشمل:
- تطبيق العميل.
- تطبيق/واجهة التاجر.
- لوحة الإدارة.
- Marketplace Engine.
- Trust Layer.
- Order Management.
- Merchant Analytics.
- Notifications.
- Support & Complaints.
- Reviews.
- Search & Discovery.
- Security, monitoring and operational controls.

البداية الجغرافية هي **كوستي** بهدف بناء كثافة محلية حقيقية قبل التوسع إلى مدن أخرى.

---

# 2. Vision

> **أن يصبح تاني الوجهة المحلية التي يبدأ منها العميل عندما يريد اكتشاف وشراء المنتجات من التجار والمشاريع المحلية، والمنصة التي يعتمد عليها التاجر للوصول إلى العملاء وإدارة تجارته الرقمية.**

## Mission

تبسيط التجارة المحلية عبر:
- جمع العرض المتناثر.
- تحسين اكتشاف المنتجات.
- توفير معلومات واضحة.
- رفع الثقة.
- تنظيم الطلبات.
- تسهيل الشراء.
- تمكين التاجر من إدارة نشاطه.
- بناء بيانات تساعد المنصة والتاجر على اتخاذ قرارات أفضل.

---

# 3. Product Thesis

التجارة المحلية الرقمية تعتمد بدرجة كبيرة على Social Commerce والمحادثات المباشرة. المشكلة ليست غياب البائعين أو المنتجات فقط، بل تشتت التجربة.

تاني يعيد تنظيم هذه العملية حول Marketplace واحد:

**Discovery → Comparison → Trust → Order → Fulfillment → Review → Repeat**

نجاح المنتج يعتمد على خلق قيمة حقيقية لكلا الطرفين:

### للعميل
> **تلقى أكتر، تحتار أقل، وتشتري بثقة.**

### للتاجر
> **تاني يجيب ليك زباين، ينظم ليك الطلبات، ويخلي تجارتك تظهر أكتر.**

---

# 4. Problem

## مشكلة العميل

العميل يواجه:
- منتجات موزعة بين Facebook وWhatsApp وInstagram وغيرها.
- صعوبة اكتشاف خيارات متعددة.
- صعوبة مقارنة المنتجات والتجار.
- تفاوت المعلومات والأسعار والتوفر.
- تفاوت مستوى الثقة.
- الاعتماد على المحادثات لإتمام الطلب.
- غياب سجل شراء منظم.
- صعوبة معرفة حالة الطلب.
- صعوبة التعامل مع المشكلات بعد الشراء.

## مشكلة التاجر

التاجر يواجه:
- الاعتماد على صفحات ومحادثات Social Media.
- ضياع أو تداخل الطلبات.
- صعوبة إدارة المنتجات والتوفر.
- ضعف أدوات المتابعة والتحليل.
- صعوبة الوصول إلى عملاء جدد.
- عدم وجود قناة Marketplace محلية منظمة.
- صعوبة معرفة أداء المنتجات والمبيعات.
- عدم وجود أدوات واضحة لبناء سجل تجاري رقمي.

---

# 5. Target Market

## السوق الأول

**كوستي – السودان**

اختيار مدينة واحدة في البداية يهدف إلى بناء:
- Supply density.
- Demand density.
- Merchant relationships.
- Trust.
- Operational knowledge.
- Local brand awareness.

بعد بناء نموذج تشغيلي قابل للتوسع، يمكن التوسع تدريجياً إلى مدن أخرى.

## التوسع

**كوستي → مدينة ثانية → مدن إضافية → تغطية أوسع**

لا يتم التوسع لمجرد زيادة عدد المستخدمين؛ بل بعد توفر:
- Supply كافٍ.
- طلب متكرر.
- Merchant retention.
- Fulfillment capability.
- Support capability.
- Economics قابلة للتحسين.

---

# 6. Target Users

## Customer ICP

مستخدم محلي:
- يعتمد على الهاتف.
- يستخدم وسائل التواصل الاجتماعي.
- يهتم بالمنتجات الشخصية والمنزلية والعائلية.
- يريد اكتشاف خيارات متعددة.
- يقدر الثقة والمعلومات الواضحة.
- يفضل تجربة طلب منظمة.

## Merchant ICP

### Online Sellers
بائع لديه نشاط قائم على:
- Facebook.
- WhatsApp.
- Instagram.
- قنوات اجتماعية أخرى.

### Home Businesses
مشاريع منزلية ترغب في:
- الوصول إلى عملاء جدد.
- تنظيم الطلبات.
- عرض المنتجات.
- بناء حضور تجاري.

### Small Stores
متاجر صغيرة يمكنها استخدام المنصة كقناة بيع رقمية إضافية.

---

# 7. Initial Categories

الأولوية التجارية الأولى:
1. ملابس النساء.
2. مستلزمات البيت.
3. الأطفال.
4. المنتجات والمشاريع المنزلية.
5. المنتجات المحلية ذات الطلب المتكرر.

يمكن إضافة فئات أخرى تدريجياً بناءً على:
- Demand.
- Supply.
- Repeat purchase.
- Merchant quality.
- Fulfillment capability.
- Economics.

---

# 8. Marketplace Model

العلاقة الأساسية:

**Customer ↔ TANI ↔ Merchant**

## مسؤولية تاني

- Discovery.
- Search.
- Product presentation.
- Store information.
- Trust layer.
- Cart.
- Checkout.
- Order orchestration.
- Order status.
- Notifications.
- Reviews.
- Support.
- Complaints.
- Merchant analytics.
- Marketplace analytics.

## مسؤولية التاجر

- صحة بيانات المنتج.
- السعر.
- التوفر.
- قبول الطلب.
- تجهيز الطلب.
- جودة المنتج.
- التوصيل.
- رسوم التوصيل التي يحددها.
- الالتزام بسياسات المنصة.

---

# 9. Multi-Merchant Orders

يسمح تاني للعميل بشراء منتجات من أكثر من تاجر.

يمكن عرض العملية للمستخدم كـ **Order Group** واحد، بينما يتم داخلياً إنشاء طلب مستقل لكل تاجر.

مثال:

- Merchant A → Order A + Delivery A.
- Merchant B → Order B + Delivery B.

كل طلب يحتفظ بـ:
- التاجر.
- المنتجات.
- الأسعار.
- رسوم التوصيل.
- الحالة.
- المسؤولية التشغيلية.

هذا يحافظ على وضوح المسؤوليات والتسويات.

---

# 10. Customer Experience

## Customer Journey

**Social / Referral / Word of Mouth**

↓

**TANI**

↓

**Home / Search / Categories**

↓

**Product**

↓

**Store**

↓

**Cart**

↓

**Checkout**

↓

**Order Group**

↓

**Merchant Orders**

↓

**Merchant Confirmation**

↓

**Preparing**

↓

**Out for Delivery**

↓

**Delivered**

↓

**Review**

↓

**Repeat / Discovery**

## مبادئ التجربة

- البساطة.
- السرعة.
- وضوح السعر.
- وضوح التوفر.
- وضوح التوصيل.
- تقليل الخطوات.
- تقليل استهلاك البيانات.
- حالات واضحة للطلبات.
- ثقة قبل الشراء.
- دعم واضح بعد الشراء.

---

# 11. Customer Features

## Discovery
- Home feed.
- Categories.
- Featured products.
- New products.
- Popular products.
- Nearby/local discovery.
- Store discovery.
- Search.

## Search
- Product search.
- Store search.
- Category filtering.
- Price filtering.
- Availability filtering.
- Sorting.
- Search suggestions.
- Empty-result handling.

## Product
- Images.
- Name.
- Description.
- Price.
- Availability.
- Merchant.
- Trust status.
- Delivery information.
- Reviews.
- Related products.

## Store
- Store profile.
- Logo/cover.
- Description.
- Categories.
- Products.
- Trust status.
- Reviews.
- Delivery information.
- Contact/support pathway.
- Store activity.

## Cart & Checkout
- Add/remove products.
- Quantity.
- Merchant grouping.
- Delivery fee.
- Order total.
- Customer information.
- Address.
- Notes.
- COD.
- Order confirmation.

## Orders
- Active orders.
- Completed orders.
- Cancelled orders.
- Merchant-level statuses.
- Delivery visibility.
- Order details.
- Support/complaint entry point.

## Account
- Profile.
- Addresses.
- Favorites.
- Notifications.
- Order history.
- Reviews.
- Settings.
- Privacy.
- Terms.

---

# 12. Merchant Product

## Merchant Onboarding

- Account creation.
- Phone verification.
- Identity information.
- Merchant information.
- Store information.
- Store category.
- Delivery areas.
- Delivery fees.
- Verification workflow.
- Agreement to merchant policies.

## Store Management

- Store profile.
- Logo.
- Cover.
- Description.
- Contact information.
- Operating status.
- Delivery settings.
- Store visibility.

## Product Management

- Add product.
- Edit product.
- Delete/deactivate product.
- Product images.
- Price.
- Description.
- Category.
- Variants.
- Stock/availability.
- Visibility.
- Featured status where applicable.

## Order Management

- New orders.
- Accepted.
- Preparing.
- Ready.
- Out for delivery.
- Delivered.
- Cancelled.
- Rejected.
- Customer details required for fulfillment.
- Order notes.
- Delivery information.

## Merchant Dashboard

- Orders.
- Sales.
- GMV.
- Product performance.
- Best sellers.
- Views.
- Conversion.
- Customer activity.
- Repeat customers where measurable.
- Operational performance.
- Cancellation rate.
- Merchant response performance.

## Merchant Value

القيمة التجارية الأساسية:
- Customers.
- Orders.
- Visibility.
- Organization.
- Sales data.

أي خدمة مدفوعة مستقبلية يجب أن تكون مرتبطة بقيمة قابلة للقياس للتاجر.

---

# 13. Merchant Trust System

يجب أن يكون Trust قابلاً للبناء والتراجع.

## مستويات الثقة

يمكن أن تتضمن:
- Verified Merchant.
- Trusted Merchant.
- High-performing Merchant.

## عوامل التقييم

- Verification.
- Order completion.
- Merchant response.
- Cancellation.
- Complaints.
- Reviews.
- Fulfillment performance.
- Product accuracy.

لا يحصل التاجر على مستوى ثقة مرتفع لمجرد التسجيل.

يمكن خفض أو تعليق مستوى الثقة عند تدهور الأداء.

---

# 14. Reviews & Reputation

يمكن للعميل تقييم:
- المنتج.
- تجربة التاجر.
- الطلب.

يجب منع:
- التقييمات الوهمية.
- التقييم الذاتي.
- التلاعب بالتقييمات.
- التقييمات غير المرتبطة بمعاملة.

تستخدم التقييمات كجزء من:
- Trust.
- Ranking.
- Merchant quality.
- Customer decision-making.

---

# 15. Delivery Model

في البداية:
- التاجر مسؤول عن التوصيل.
- التاجر يحدد مناطق التوصيل.
- التاجر يحدد رسوم التوصيل وفق سياسات المنصة.
- رسوم التوصيل تظهر للعميل قبل تأكيد الطلب.
- حالة التوصيل تظهر داخل الطلب.

## لاحقاً

يمكن لتاني بناء أو التكامل مع:
- شبكة توصيل.
- مزودي توصيل.
- أدوات إدارة التوصيل.
- تتبع أكثر تفصيلاً.

لا يتم تحويل التوصيل إلى نشاط تديره المنصة بالكامل إلا بعد إثبات الحاجة والاقتصاديات.

---

# 16. Payments

## البداية

**Cash on Delivery**

لأنه يتناسب مع النموذج التشغيلي الأول للمنصة.

## البنية المستقبلية

يجب تصميم طبقة دفع قابلة لإضافة:
- Electronic Payments.
- Payment Providers.
- Wallets.
- Payment verification.
- Refund handling.

لا يتم ربط منطق الطلب مباشرة بطريقة دفع واحدة.

---

# 17. Notifications

إشعارات العميل:
- Order received.
- Merchant accepted.
- Preparing.
- Out for delivery.
- Delivered.
- Cancelled.
- Review request.
- Important account notifications.

إشعارات التاجر:
- New order.
- Order reminder.
- Customer/order update.
- Stock-related alert.
- Account/verification update.
- Performance alerts.

الإشعارات يجب أن تكون مفيدة وليست مزعجة.

---

# 18. Support & Complaints

يجب توفير مسار واضح:
- مشكلة في الطلب.
- المنتج غير متوفر.
- التاجر لم يرد.
- تأخر التوصيل.
- مشكلة في المنتج.
- مشكلة في الرسوم.
- إلغاء.
- نزاع.

## Admin Resolution

Admin يستطيع:
- رؤية الطلب.
- رؤية الأطراف.
- رؤية سجل الحالات.
- التواصل عند الحاجة.
- تسجيل القرار.
- تصعيد المشكلة.
- تطبيق سياسة المنصة.
- تسجيل السبب.

---

# 19. Admin Platform

## Merchant Management

- Applications.
- Verification.
- Approval.
- Suspension.
- Rejection.
- Trust status.
- Performance.

## Product Management

- Review.
- Moderation.
- Categories.
- Visibility.
- Reports.
- Prohibited products.

## Order Management

- All orders.
- Merchant orders.
- Order groups.
- Status.
- Cancellations.
- Complaints.
- Operational intervention.

## Customer Management

- Accounts.
- Orders.
- Complaints.
- Reviews.
- Account status.

## Analytics

- GMV.
- Orders.
- Completion.
- Cancellation.
- Repeat.
- Active merchants.
- Merchant retention.
- Category performance.
- Search performance.
- Delivery performance.

## Configuration

- Categories.
- Fees.
- Policies.
- Banners.
- Featured content.
- Notifications.
- System settings.

---

# 20. Search & Discovery Engine

الهدف ليس مجرد مطابقة كلمات.

Ranking يمكن أن يعتمد تدريجياً على:
- Relevance.
- Availability.
- Merchant quality.
- Product quality.
- Popularity.
- Freshness.
- Conversion.
- Customer behavior.
- Location where applicable.

يجب ألا يسمح الترتيب المدفوع بتدمير جودة نتائج البحث.

---

# 21. Recommendation System

يمكن تطوير التوصيات تدريجياً بناءً على:
- Browsing.
- Search.
- Purchases.
- Categories.
- Favorites.
- Merchant interactions.

تبدأ القواعد بسيطة ثم تتحول إلى نظام أكثر ذكاءً عند توفر بيانات كافية.

---

# 22. Monetization

النموذج التجاري يجب أن يوازن بين:
- نمو Marketplace.
- قيمة التاجر.
- سهولة دخول التجار.
- جودة تجربة العميل.
- استدامة الشركة.

## Revenue Streams

### 1. Merchant Subscription
باقات مدفوعة لأدوات ذات قيمة حقيقية.

### 2. Featured Placement
إظهار مدفوع مع وضوح أنه ترويج مدفوع، دون التضحية بجودة النتائج.

### 3. Transaction / Service Fee
نسبة أو رسوم مرتبطة بالمعاملات إذا كان النموذج والاقتصاديات يدعمان ذلك.

### 4. Advertising
إعلانات تجارية مناسبة داخل Marketplace.

### 5. Premium Merchant Tools
أدوات متقدمة مثل:
- Analytics.
- Promotions.
- Customer insights.
- Advanced inventory.
- Advanced reporting.

### 6. Delivery Revenue
إذا أصبحت المنصة تدير أو تنظم خدمات توصيل مباشرة.

## مبدأ أساسي

لا يتم فرض رسوم على التاجر لمجرد وجوده.

يجب أن تكون الرسوم مرتبطة بقيمة قابلة للقياس.

---

# 23. Pricing Strategy

لا يتم تثبيت أسعار الباقات بشكل عشوائي.

يجب أن تعتمد القرارات على:
- Merchant willingness to pay.
- Merchant ROI.
- GMV.
- Orders per merchant.
- Cost to serve.
- Churn.
- Value of premium features.

## تضارب المصالح

يجب منع أن تصبح المنصة منحازة لمن يدفع أكثر على حساب العميل.

Featured content يجب أن يكون:
- واضحاً.
- مضبوطاً.
- خاضعاً للجودة.
- غير مضلل.

---

# 24. Growth Strategy

## Merchant-led Distribution

التاجر نفسه يصبح قناة اكتساب:

Merchant joins  
→ adds products  
→ shares store/products  
→ brings existing audience  
→ receives orders  
→ sees value  
→ refers other merchants.

## Customer Acquisition

- Facebook.
- WhatsApp.
- Instagram.
- Local communities.
- Referral.
- Word of mouth.
- Local creators.
- Merchant sharing.
- Content.
- Product discovery.

## Growth Loops

### Merchant Loop
More merchants → more products → better discovery → more customers → more orders → more merchant value → more merchants.

### Customer Loop
More customers → more demand → better merchant economics → more supply → better selection → more customers.

### Trust Loop
More completed transactions → better reputation data → higher trust → more conversions → more transactions.

---

# 25. Retention

## Customer Retention

- Better discovery.
- Personalized home.
- New products.
- Reliable merchants.
- Order history.
- Favorites.
- Repeat purchase.
- Reviews.
- Useful notifications.

## Merchant Retention

- Orders.
- Incremental customers.
- Sales data.
- Operational tools.
- Visibility.
- Performance insights.
- Promotions.
- Customer retention tools.

---

# 26. Competitive Advantage

عند الإطلاق لا يوجد Moat مضمون.

الهدف هو بناء Moat مع الوقت عبر:

1. Local density.
2. Transaction history.
3. Trust graph.
4. Merchant reputation.
5. Customer behavior data.
6. Merchant success.
7. Brand.
8. Marketplace liquidity.
9. Operational knowledge.
10. Habit.

## Network Effects

كلما زاد عدد التجار:
- زادت الخيارات.
- زادت قيمة البحث.
- زادت احتمالية العثور على المنتج.

كلما زاد عدد العملاء:
- زادت قيمة الانضمام للتجار.

هذا هو Network Effect المستهدف، وليس ميزة موجودة تلقائياً منذ اليوم الأول.

---

# 27. Sudan Market Considerations

المنتج يجب أن يكون مصمماً لظروف السوق المستهدف.

## Connectivity

- Low bandwidth.
- صور مضغوطة.
- WebP.
- Lazy loading.
- Pagination.
- Cache.
- تقليل البيانات غير الضرورية.
- تجنب تشغيل الفيديو تلقائياً.

## Devices

- دعم أجهزة Android متنوعة.
- استهلاك RAM منخفض قدر الإمكان.
- Startup سريع.
- تجنب المؤثرات الثقيلة.

## Social Commerce

يجب أن يعمل Social Media كقناة اكتساب وليس كمنافس فقط.

المستخدم يستطيع الوصول من:
- WhatsApp.
- Facebook.
- Instagram.
- روابط مشاركة المنتجات والمتاجر.

## Trust

- Verification.
- Reviews.
- Merchant performance.
- Complaint history.
- Clear policies.

## COD

يجب قياس:
- Acceptance.
- Cancellation.
- Delivery success.
- Customer reliability.

## Addresses

العنوان يجب أن يكون عملياً ومناسباً للسياق المحلي، مع:
- وصف العنوان.
- المنطقة.
- معلم قريب عند الحاجة.
- رقم الهاتف.
- ملاحظات التوصيل.

---

# 28. Technical Architecture

البنية المستهدفة لتاني هي بنية **Modular, Testable, Low-Bandwidth First** بدون إعادة كتابة المشروع بالكامل دفعة واحدة.

## Client

**Android / Kotlin / Jetpack + Material 3**

المسار المعماري الإلزامي:

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
Supabase / Local Cache
```

لا تتصل أي شاشة مباشرة بـ Supabase.

## Project Structure

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

## Repository Boundaries

بدلاً من Repository واحد ضخم، يعتمد المشروع على:

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

## Use Cases

أمثلة الحالات الأساسية:

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

## Session & Local State

- `SessionManager` لإدارة دورة الجلسة.
- `SecureTokenStorage` للتوكنات الحساسة باستخدام Android Keystore + تخزين محلي مشفر.
- `Jetpack DataStore` للإعدادات والحالات غير الحساسة مثل onboarding، المدينة، الإشعارات، وخيارات العرض.
- حذف بيانات الجلسة الحساسة عند Logout.
- دعم refresh token rotation وانتهاء الجلسة بدون race conditions.

## Network Layer

طبقة موحدة تتكون من:

```text
ApiClient
AuthInterceptor
ErrorMapper
RetryPolicy
NetworkMonitor
```

وتوحّد:
- timeout.
- parsing.
- retry rules.
- unauthorized handling.
- token refresh.
- network state.
- رسائل الأخطاء القابلة للعرض للمستخدم.

## Backend

**Supabase + PostgreSQL**

يستخدم لـ:
- Authentication.
- PostgreSQL.
- Storage.
- RLS/RBAC.
- Server-side RPCs للعمليات الحساسة.
- Triggers عند الحاجة فقط.

الخادم هو المرجع النهائي للأسعار، المخزون، الرسوم، الخصومات، الصلاحيات، والإجماليات.

## Admin

لوحة إدارة Web مستقلة بصلاحيات server-side، مع فصل واضح بين:
- `admin`
- `support`

ولا يعتبر إخفاء عنصر في الواجهة وسيلة حماية.

## Replaceable Platform Interfaces

```text
PaymentProvider
DeliveryProvider
NotificationProvider
SearchProvider
AnalyticsProvider
StorageProvider
RecommendationProvider
```

## Notifications

يبدأ النظام بـ **FCM** عبر `NotificationProvider` حتى يمكن استبداله أو توسيعه لاحقاً.

---

# 29. Database Core

الجداول الأساسية المقترحة:

- users
- profiles
- merchant_profiles
- merchant_verifications
- stores
- categories
- products
- product_images
- product_variants
- inventory
- addresses
- carts
- cart_items
- order_groups
- orders
- order_items
- order_status_history
- delivery_settings
- reviews
- complaints
- notifications
- favorites
- merchant_metrics
- audit_logs
- featured_placements
- subscriptions
- payments
- refunds
- system_settings

## Mandatory Database Rules

العلاقات والصلاحيات تصمم حول:
- ownership.
- merchant isolation.
- customer privacy.
- order integrity.
- admin/support authority.

القواعد الإلزامية:
- لا يوجد جدول public بدون RLS.
- لا توجد privileged RPC متاحة لـ `anon`.
- كل RPC حساس يتحقق من `auth.uid()` وrole وownership وaccount status.
- تحديد `search_path` صراحة في الدوال الحساسة.
- تقليل `SECURITY DEFINER` لأقل حد ممكن.
- إضافة indexes للأعمدة المستخدمة داخل RLS والاستعلامات الحرجة.
- منع direct writes للعمليات التي يجب أن تمر عبر RPC.

## Commerce RPC Layer

يجب أن تتضمن طبقة المعاملات على الأقل:

```text
quote_cart()
checkout_atomic()
transition_order_status()
cancel_order()
```

`quote_cart()` يعيد من الخادم:

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

`checkout_atomic()` ينفذ إنشاء الطلب والمخزون والتاريخ داخل transaction واحدة قدر الإمكان، مع idempotency key.

## Auditability

الإجراءات الحساسة تحفظ في `audit_logs` مع:
- منفذ الإجراء.
- وقت التنفيذ.
- الكيان المستهدف.
- السبب.
- القيمة القديمة والجديدة عند الحاجة.

---

# 30. Security

الأمان أولوية P0 ولا يؤجل إلى ما بعد إضافة الميزات.

## Session Security

- عدم حفظ Access Token أو Refresh Token في SharedPreferences عادي.
- استخدام Android Keystore + encrypted local storage.
- حذف التوكنات عند Logout.
- التعامل مع refresh token rotation.
- session expiration handling.
- منع refresh race conditions.
- اختبار expired access token وrevoked refresh token وoffline refresh failure.

## Password Reset

يمنع استخدام deep link مخصص غير موثق مثل:

```text
tani://auth/reset
```

ويستخدم بدلاً منه:

```text
https://<TANI_DOMAIN>/auth/reset
```

مع:
- Android Verified App Links.
- `autoVerify=true`.
- `assetlinks.json`.

## Supabase Security Audit

يجب مراجعة:

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

## Supabase Auth Hardening

تفعيل حيثما أمكن:

```text
Leaked Password Protection
Password minimum strength
Email verification
Rate limits
Session controls
```

ومراجعة:
- signup abuse.
- password reset abuse.
- brute force.
- account enumeration.

## Admin & Support Authorization

- كل صلاحية إدارية تتحقق داخل PostgreSQL أو طبقة server-side موثوقة.
- فصل admin عن support.
- تسجيل Audit Log لكل إجراء حساس.
- لا تعتمد الحماية على عناصر الواجهة.

## Business Logic Security

لا يعتمد الخادم على أي قيمة حساسة مرسلة من Android في:

```text
Price
Stock
Delivery fee
Discount
Commission
Subscription entitlement
Featured status
Order totals
Role / permissions
```

## Secrets

لا تحفظ الأسرار داخل:
- GitHub repository.
- Android source.
- Admin source.

تستخدم:
- GitHub Secrets.
- Supabase secrets.
- Environment variables.

## Security Test Matrix

يجب أن تفشل السيناريوهات التالية:

```text
Customer → another customer data
Customer → merchant private data
Merchant → another merchant orders
Merchant → admin RPC
Anonymous → protected RPC
Support → admin-only action
```

---

# 31. Images & Storage

طبقة الصور والتخزين يجب أن تكون آمنة وخفيفة على الشبكات الضعيفة.

## Upload & Storage

- ضغط الصور قبل الرفع قدر الإمكان.
- توليد أحجام مناسبة لكل موضع عرض.
- WebP/AVIF عند الملاءمة.
- حماية الملفات الخاصة عبر Storage policies.
- تنظيم المسارات حسب المستخدم/التاجر/المنتج.
- منع الوصول المتقاطع بين التجار.
- سياسة حذف وتنظيف للملفات غير المستخدمة.

## Client Loading

تستخدم مكتبة تحميل صور مناسبة توفر:
- memory cache.
- disk cache.
- resize.
- placeholders.
- failure state.
- cancellation عند خروج العنصر من الشاشة.

لا تحمل الصورة الأصلية كبيرة الحجم إذا كانت البطاقة تحتاج Thumbnail فقط.

---

# 32. Performance

تاني **Low-Bandwidth First** ويجب أن يعمل بصورة مقبولة على أجهزة وشبكات محدودة.

## Required Techniques

```text
Paging 3
Room cache where justified
Image memory/disk cache
Retry with exponential backoff
Offline states
Network monitoring
Timeout handling
Skeleton loaders
Image compression
Lazy loading
Debounced search
```

## Local Cache

يمكن استخدام Room لـ:
- Home cache.
- Products cache.
- Categories.
- Search results.
- Stores.
- Orders read cache.

لا يخزن الكاش بيانات حساسة بلا حاجة ولا يصبح مصدر الحقيقة للعمليات المالية.

## Paging

يستخدم Paging 3 في:
- Products.
- Search.
- Stores.
- Orders.
- Reviews.
- Admin lists.

## Performance Validation

إضافة:

```text
Macrobenchmark
Baseline Profiles
```

للشاشات/المسارات:
- Startup.
- Home.
- Search.
- Product.
- Cart.
- Checkout.

---

# 33. Poor Network & Resilience

يجب ألا تنهار التجربة عند ضعف أو انقطاع الشبكة.

يشمل ذلك:
- Cache للمحتوى القابل للقراءة.
- NetworkMonitor موحد.
- Retry مع exponential backoff للعمليات المناسبة فقط.
- timeout واضح.
- Offline state مخصص بدلاً من شاشة فارغة.
- حفظ إدخالات المستخدم المهمة عند الانقطاع.
- عدم تكرار العمليات الحساسة تلقائياً بلا idempotency.
- إظهار حالة البيانات المخزنة محلياً بوضوح عند الحاجة.

## Sensitive Operations

للـCheckout والعمليات التي تنشئ أثراً مالياً/تجارياً:
- idempotency key إلزامي.
- لا يعاد الطلب بشكل أعمى بعد timeout.
- بعد reconnect يتم التحقق من نتيجة العملية السابقة قبل إعادة المحاولة.

---

# 34. Order Integrity

منطق التجارة والطلبات يخضع لقاعدة **Server Authority**.

## Cart Model

السلة المحلية لا تحفظ Product كامل كمصدر حقيقة. تحفظ فقط:

```text
productId
variantId
quantity
addedAt
```

وقبل التأكيد يتم جلب الحالة الحالية من الخادم.

## Server Quote

قبل إنشاء الطلب يعرض التطبيق نتيجة `quote_cart()` التي تتضمن:
- السعر الحالي.
- المخزون الحالي.
- حالة توفر المنتج.
- حالة التاجر.
- رسوم التوصيل.
- الخصومات.
- subtotal.
- grand total.
- validation warnings.

## Checkout Flow

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

## Idempotency

تطبق idempotency على Checkout ويختبر:
- double-click checkout.
- retry after timeout.
- network reconnect.
- duplicated API request.
- concurrent checkout.

## Inventory Concurrency

يمنع `stock < 0` بواسطة transaction-level locking أو atomic update داخل PostgreSQL.

اختبار إلزامي:

```text
آخر قطعة
+
عميلان
+
Checkout في نفس اللحظة
```

يجب أن ينجح طلب واحد فقط.

## Order Snapshot

يحفظ الطلب Snapshot من:
- اسم المنتج.
- السعر.
- الكمية.
- التاجر.
- رسوم التوصيل.
- الإجمالي.
- بيانات العميل اللازمة للتنفيذ.

## Order State Machine

الحالات الرسمية:

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

الانتقالات الأساسية:

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

أي انتقال غير مصرح به يرفضه الخادم ويسجل الحدث في `order_status_history`.

## Cancellation

يحدد النظام صراحة:
- متى يستطيع العميل الإلغاء.
- متى يستطيع التاجر الإلغاء.
- متى يلزم Admin.
- إعادة المخزون تلقائياً.
- سبب الإلغاء.
- تحديث analytics.

## Delivery & Payment Abstractions

```text
DeliveryProvider
PaymentProvider
```

الحالة الأولى:
- `MerchantDeliveryProvider`
- `CashOnDeliveryProvider`

مع قابلية إضافة Platform delivery / Third-party / Pickup والدفع البنكي أو المحافظ أو بوابات الدفع بدون إعادة كتابة Order Engine.

---

# 35. Analytics, Monitoring & Observability

## Product Analytics Events

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

## Marketplace

- Orders.
- Completed orders.
- Cancellation.
- Completion rate.
- Merchant response.
- Delivery success.
- Search success.
- Stock failure.

## Customer

- Active users.
- Search behavior.
- Product views.
- Add to cart.
- Checkout.
- Orders.
- Repeat orders.
- Category engagement.

## Merchant

- Product views.
- Orders.
- Conversion.
- GMV.
- Cancellation.
- Response time.
- Repeat customers.
- Best sellers.

## Business

- GMV.
- Revenue.
- ARPU.
- CAC.
- Merchant CAC.
- Churn.
- Contribution margin.
- LTV.

## Crash & Error Monitoring

يجب تسجيل:

```text
Crash
ANR
Fatal errors
Network errors
Database errors
Checkout failures
```

## Operational Alerts

تنبيهات عند:

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

# 36. Operational Model

تاني ليس مجرد Software.

يحتاج إلى Operating System واضح لإدارة:
- Merchant onboarding.
- Verification.
- Product moderation.
- Order exceptions.
- Complaints.
- Cancellations.
- Delivery problems.
- Fraud/abuse.
- Customer support.
- Merchant support.

## SLA

يجب تعريف:
- زمن الرد.
- زمن قبول الطلب.
- زمن التجهيز.
- التعامل مع التأخير.
- التعامل مع عدم توفر المنتج.
- التعامل مع الشكاوى.

---

# 37. Merchant Policies

كل تاجر يوافق على:
- صحة بياناته.
- صحة المنتجات.
- الأسعار.
- التوفر.
- تنفيذ الطلبات.
- جودة المنتج.
- التوصيل.
- الالتزام بسياسات المنصة.
- عدم التلاعب بالتقييمات.
- عدم استخدام المنصة في الأنشطة المحظورة.

المخالفات قد تؤدي إلى:
- تحذير.
- خفض Trust.
- إيقاف منتجات.
- تعليق الحساب.
- إنهاء العلاقة.

---

# 38. Customer Policies

يجب توضيح:
- الطلب.
- الإلغاء.
- التوصيل.
- الدفع.
- الاستلام.
- الشكاوى.
- التقييمات.
- المنتجات المحظورة.
- الخصوصية.
- مسؤوليات العميل.

---

# 39. Legal & Compliance

يجب تجهيز:
- Terms of Service.
- Privacy Policy.
- Merchant Agreement.
- Refund & Cancellation Policy.
- Delivery Policy.
- Review Policy.
- Prohibited Products Policy.
- Complaint & Dispute Policy.
- Data retention/deletion policy.

هذه الوثائق يجب أن تتوافق مع القوانين والتنظيمات المحلية المعمول بها عند الإطلاق.

---

# 40. Data & Privacy

يجب جمع الحد الأدنى اللازم من بيانات المستخدم.

يجب تعريف:
- لماذا تجمع البيانات.
- أين تخزن.
- من يستطيع الوصول إليها.
- مدة الاحتفاظ.
- متى تحذف.
- كيف يتعامل النظام مع طلبات الحساب والبيانات.

بيانات الهوية الخاصة بالتجار يجب حمايتها بدرجة أعلى من بيانات العرض العامة.

---

# 41. Quality Standards

## Product Quality

المنتج يجب أن يحتوي على:
- اسم واضح.
- سعر.
- صور مناسبة.
- وصف.
- فئة.
- توفر.
- معلومات التوصيل ذات الصلة.

## Merchant Quality

يجب مراقبة:
- Response.
- Acceptance.
- Completion.
- Cancellation.
- Complaints.
- Reviews.
- Product accuracy.

---

# 42. Launch Readiness

لا يعتبر تاني جاهزاً للإطلاق Production إلا بعد تحقق المتطلبات التجارية والتقنية التالية.

## Marketplace & Operations

- Supply حقيقي ومنظم.
- تجار مقبولون وموثقون.
- منتجات حقيقية وأسعار وتوفر واضح.
- مسار طلب كامل.
- COD يعمل.
- آلية توصيل واضحة.
- سياسات الإلغاء والشكاوى.
- Admin قادر على التدخل.
- Support channel.
- Terms / Privacy / Merchant / Delivery / Cancellation متاحة.

## Security Gate

- Critical security issues = 0.
- High security issues = 0.
- جميع RLS policies مختبرة.
- RPC permissions مختبرة.
- Password Reset عبر Verified App Links.
- Admin authorization server-side.
- Secrets خارج source control.

## Commerce Gate

- Server quote يعمل.
- Atomic checkout يعمل.
- Idempotency مختبرة.
- Checkout concurrency مختبر.
- Inventory race conditions مختبرة.
- Order transitions مختبرة.
- Cancellation وإعادة المخزون مختبران.

## Quality Gate

- Unit tests ناجحة.
- Repository tests ناجحة.
- Database/RLS/Security tests ناجحة.
- UI tests للمسارات الحرجة ناجحة.
- End-to-end purchase flow ناجح.
- اختبار شبكة ضعيفة ومنقطعة ناجح.
- لا توجد regressions معروفة في المسارات الأساسية.

## Release & Operations Gate

- Staging environment منفصل.
- Release build ناجح.
- Signed AAB جاهز.
- R8/ProGuard مفعل ومختبر.
- Crash monitoring يعمل.
- Analytics تعمل.
- Backup/restore strategy موجودة.
- Operational alerts مفعلة.
- تجربة المستخدم مختبرة على أجهزة حقيقية.

---

# 43. Product Development & Modernization Phases

> التطوير يستمر تدريجياً، لكن إصلاح الأساس والأمان والمنطق التجاري يسبق التوسع في الميزات.

## Track A — Maintenance & Production Readiness

### Phase 0 — Baseline Freeze

- اعتماد آخر نسخة ناجحة على `main` كنقطة مرجعية.
- إنشاء branch صيانة مخصص.
- إنشاء baseline tag.
- حفظ Supabase schema وRLS وRPCs وStorage/Auth settings.
- إنشاء `CURRENT_KNOWN_ISSUES.md`.
- إضافة changelog رسمي.
- منع Features جديدة حتى تثبيت الأساس.

### Phase 1 — Security Hardening

- Secure session storage.
- Android Keystore.
- DataStore للبيانات غير الحساسة.
- Verified App Links لاستعادة كلمة المرور.
- Supabase RLS/RPC/Storage audit.
- Auth hardening.
- Admin/support authorization audit.

### Phase 2 — Architecture Refactor

- تقسيم Repository.
- تقسيم Supabase access layer.
- ViewModels.
- Use Cases.
- Network layer موحد.
- Dependency boundaries واضحة.

### Phase 3 — Commerce Hardening

- Cart redesign.
- Server quote.
- Atomic checkout.
- Idempotency.
- Inventory concurrency.
- Order state machine.
- Cancellation rules.
- Delivery/Payment abstractions.

### Phase 4 — Automated Testing

- Unit.
- Repository.
- Database.
- RLS/Security.
- UI.
- End-to-End.
- Checkout concurrency.

### Phase 5 — UI/UX Modernization

- Design System.
- Material 3.
- RTL/Arabic/accessibility.
- Compose migration تدريجية.
- Home/Search/Product/Cart/Checkout redesign.

### Phase 6 — Performance & Offline

- Room cache عند الحاجة.
- Paging 3.
- Image caching.
- Offline UX.
- Retry strategy.
- Baseline Profiles.
- Macrobenchmark.

### Phase 7 — CI/CD & Release

- Dependency verification.
- Static analysis.
- Android Lint.
- Tests in CI.
- Debug + Release builds.
- Signed AAB.
- R8.
- Artifacts + release notes.

### Phase 8 — Monitoring & Operations

- Crash/ANR monitoring.
- Product analytics.
- Merchant analytics.
- Operational alerts.

### Phase 9 — Admin Modernization

- الاحتفاظ باللوحة الحالية مؤقتاً إن لزم.
- نقل تدريجي إلى Web Admin حديث.
- فصل admin/support.
- Audit logs.

### Phase 10 — Future-Ready Platform

تجهيز البنية للدفع الإلكتروني، التوصيل، coupons، loyalty، AI search/recommendations، web/iOS، B2B، APIs، ومدن جديدة بدون إعادة كتابة الأساس.

## Track B — Product Capability Delivery

بعد استقرار Track A، يستمر بناء قدرات المنتج حسب الأولوية التجارية:

1. Marketplace Core: Categories, Home, Search, Products, Stores, Discovery.
2. Commerce: Cart, Checkout, Addresses, COD, Orders.
3. Merchant Operations: Onboarding, Verification, Catalog, Inventory, Orders, Delivery.
4. Trust & Support: Reviews, Reputation, Complaints, Moderation.
5. Growth: Sharing, Referrals, Notifications, Discovery improvements.
6. Monetization: Subscriptions, Premium tools, Featured placement, Fees, Advertising.
7. Scale: Search, Recommendations, Analytics, Delivery integrations, More cities/categories.

---

# 44. Growth Operating System

يجب إنشاء نظام أسبوعي لمراقبة:

## Marketplace
- Orders.
- Completion.
- Cancellation.
- Search success.
- Supply availability.

## Customer
- Active users.
- First purchase.
- Repeat purchase.
- Category engagement.

## Merchant
- Active merchants.
- Merchants receiving orders.
- Merchant response.
- Merchant retention.
- Merchant GMV.

## Reliability
- Stock failures.
- Merchant failures.
- Delivery failures.
- Complaints.

## Business
- GMV.
- Revenue.
- CAC.
- Merchant CAC.
- Contribution margin.
- Churn.

---

# 45. Unit Economics

يجب عدم اختراع الأرقام.

يجب قياس:

## Customer

- CAC.
- Activation rate.
- First-order conversion.
- AOV.
- Orders/customer.
- Repeat rate.
- Cancellation.
- Contribution/order.
- LTV.

## Merchant

- Merchant CAC.
- Activation.
- Time to first order.
- Orders/merchant.
- GMV/merchant.
- Revenue/merchant.
- Churn.
- WTP.
- Merchant ROI.

## Marketplace

- GMV.
- Take rate.
- Revenue/order.
- Cost/order.
- Support/order.
- Delivery-related cost.
- Contribution margin.
- Marketplace liquidity.

---

# 46. North Star & KPIs

## North Star Metric

**Completed Orders**

لأنها تربط:
- Customer demand.
- Merchant supply.
- Fulfillment.
- Marketplace liquidity.

## Supporting Metrics

### Demand
- Active customers.
- Search success.
- Add to cart.
- Checkout conversion.
- Repeat.

### Supply
- Active merchants.
- Active products.
- Availability.
- Merchant response.

### Fulfillment
- Acceptance.
- Completion.
- Delivery success.
- Cancellation.

### Trust
- Complaints.
- Rating.
- Repeat.
- Merchant quality.

### Business
- GMV.
- Revenue.
- CAC.
- LTV.
- Contribution margin.

---

# 47. Decision Framework

أي قرار منتج أو Feature يجب أن يجيب:

1. ما المشكلة التي يحلها؟
2. لمن؟
3. ما أثره على Marketplace liquidity؟
4. هل يزيد الطلب؟
5. هل يزيد العرض؟
6. هل يزيد الثقة؟
7. هل يقلل الاحتكاك؟
8. هل يزيد الاحتفاظ؟
9. هل يخلق تكلفة تشغيلية كبيرة؟
10. هل يمكن قياس أثره؟

إذا لم يحقق قيمة واضحة، لا يتم بناؤه لمجرد زيادة عدد الميزات.

---

# 48. Anti-Feature Creep

تاني منتج كامل، لكن "كامل" لا يعني إضافة كل شيء.

أي Feature جديدة يجب تقييمها مقابل:
- Customer value.
- Merchant value.
- Business impact.
- Operational complexity.
- Technical complexity.
- Data requirements.
- Security implications.

الهدف هو **منتج متكامل ومتماسك** وليس تطبيقاً مزدحماً.

---

# 49. Future Capabilities

بعد بناء Marketplace قوي يمكن إضافة:

- Advanced recommendations.
- AI shopping assistant.
- Image search.
- Voice search.
- Loyalty.
- Coupons.
- Promotions.
- Merchant CRM.
- Advanced inventory.
- Customer segmentation.
- Merchant financing partnerships where legally and commercially appropriate.
- Logistics platform.
- B2B capabilities.
- API/integrations.
- Additional payment methods.

هذه قدرات مستقبلية وليست متطلبات لإطلاق كل وظيفة في اليوم نفسه.

---

# 50. Expansion Strategy

## Geographic

كوستي أولاً.

بعد تحقق:
- Demand.
- Supply.
- Repeat.
- Merchant retention.
- Fulfillment.
- Economics.

يتم اختيار المدينة التالية بناءً على:
- حجم الفرصة.
- سهولة التشغيل.
- كثافة التجار.
- الطلب.
- القدرة على بناء Supply.

## Category

إضافة الفئات بناءً على بيانات الاستخدام، لا لمجرد زيادة عدد المنتجات.

---

# 51. Business Risks

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| ضعف الطلب | Medium | High | تحسين Discovery وAcquisition وCategory focus |
| ضعف Supply | Medium | High | Merchant acquisition وOnboarding |
| Merchant churn | High | High | إثبات ROI وتحسين الأدوات |
| ضعف Repeat | High | High | تحسين assortment والثقة والتجربة |
| فشل التوصيل | Medium/High | High | SLA ومراقبة الأداء وشركاء توصيل لاحقاً |
| COD cancellations | Medium/High | High | تحسين confirmation وقياس السلوك |
| ضعف الشبكة | High | Medium | Cache وصور خفيفة وتجربة resilient |
| مشاكل الثقة | Medium | High | Verification وReviews وMerchant scoring |
| تقليد المنافسين | High | Medium | Density + Trust + Data + Brand |
| ضعف monetization | High | High | تجارب تسعير مرتبطة بقيمة التاجر |

---

# 52. Success Conditions

نجاح تاني لا يقاس بعدد التنزيلات فقط.

يجب أن نرى:

### Customer
- استخدام فعلي.
- طلبات مكتملة.
- تكرار شراء.
- ثقة.
- انخفاض الاحتكاك.

### Merchant
- نشاط مستمر.
- منتجات محدثة.
- استجابة جيدة.
- طلبات.
- احتفاظ.
- ROI.

### Marketplace
- Liquidity.
- Supply density.
- Demand density.
- Fulfillment reliability.

### Business
- GMV متزايد.
- Revenue قابل للنمو.
- Contribution economics تتحسن.
- Churn تحت السيطرة.

---

# 53. Product Principles

1. **Trust before growth.**
2. **Transactions before vanity metrics.**
3. **Local density before geographic expansion.**
4. **Merchant success before merchant monetization.**
5. **Simple UX before feature volume.**
6. **Low-bandwidth first.**
7. **Operational reliability is part of the product.**
8. **Data-informed decisions.**
9. **Security by design.**
10. **Build for repeat behavior.**

---

# 54. Brand Positioning

## الاسم

**تاني | TANI**

الاسم قصير وسهل التذكر، ويجب أن تكون العلامة مستقلة عن وصف مباشر مثل "سوق" أو "متجر".

## Positioning

> **تاني — مكانك لاكتشاف وشراء المنتجات من التجار المحليين بسهولة وثقة.**

## Tone

- محلي.
- بسيط.
- موثوق.
- حديث.
- قريب من المستخدم.
- غير رسمي بدرجة مناسبة.

---

# 55. Final Product Blueprint

## تاني في جملة

> **تاني Marketplace محلي يبدأ من كوستي، يجمع منتجات البائعين والمتاجر والمشاريع المنزلية في مكان واحد، ويجعل اكتشاف المنتجات وطلبها من تجار موثوقين أسهل وأكثر تنظيماً.**

## Customer

Discovery + Search + Trust + Order + COD + Delivery visibility + Repeat.

## Merchant

Store + Products + Orders + Delivery + Visibility + Sales data + Merchant tools.

## Admin

Verification + Moderation + Orders + Complaints + Analytics + Configuration.

## Monetization

Merchant subscriptions + Premium tools + Featured placement + Transaction/service fees + Advertising + Delivery revenue where appropriate.

## Technology

Android/Kotlin + Supabase/PostgreSQL + Web Admin + FCM + RLS/RBAC + Monitoring + Analytics.

## Launch Market

**كوستي – السودان**

## Long-term Advantage

**Local density + Transactions + Trust + Data + Merchant success + Habit + Brand**

---

# 56. Final Business Thesis

> **إذا استطاع تاني أن يجمع Supply محلياً جيداً، ويجعل اكتشاف المنتجات أسهل من القنوات المشتتة، ويخلق معاملات موثوقة ومكتملة، ويمنح التاجر مبيعات وقيمة قابلة للقياس، فإن Marketplace يمكن أن يتحول إلى قناة تجارة محلية ذات قيمة متراكمة يصعب نسخها مع الوقت.**

---

# 57. Final Execution Principle

> **نبني تاني كمنتج كامل ومتماسك، لكن نطوره على مراحل تنفيذية واضحة، ونقيس أثر كل مرحلة على العميل والتاجر والـMarketplace والاقتصاديات قبل الانتقال إلى مستوى أكبر من التعقيد.**

# 58. Automated Testing Standard

Build success وحده لا يعتبر دليلاً على صحة المشروع.

## Unit Tests

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

## Repository Tests

```text
AuthRepository
CartRepository
OrderRepository
MerchantRepository
```

باستخدام fake/mock APIs.

## Database & Security Tests

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

## UI Tests

المسارات الحرجة:

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

## End-to-End

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

# 59. UI/UX Modernization Standard

## Design System

```text
TaniTheme
TaniColors
TaniTypography
TaniSpacing
TaniShapes
TaniElevation
```

المكونات الموحدة:

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

## Material 3

التطبيق يدعم:
- RTL.
- Arabic typography.
- dynamic font scaling.
- dark mode.
- accessibility.

## Compose Migration

الهجرة تدريجية وليست إعادة كتابة كاملة:

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

بعد اكتمال الهجرة يمكن الانتقال الكامل إلى Navigation Compose.

## Home

- LazyColumn بدلاً من ScrollView + LinearLayouts الكبيرة.
- Search Bar.
- Banner.
- Categories LazyRow.
- Featured products.
- Recommended products.
- Newest products.
- Top-rated products.
- Stores.

## Product Lists

`LazyVerticalGrid` أو RecyclerView خلال مرحلة XML.

كل بطاقة تعرض فقط:
- Image.
- Name.
- Price.
- Store.
- Rating.
- Stock state.
- Favorite.
- Featured label.

## Search

يدعم تدريجياً:
- Arabic normalization.
- Typo tolerance.
- Ranking.
- Recent searches.
- Suggestions.
- Filters/Sort.
- Category/Price/Availability/Merchant.

## Product Details

- Image gallery.
- Variant selector.
- Stock state.
- Delivery information.
- Merchant trust.
- Ratings/Reviews.
- Related products.
- Sticky Add to Cart.

## Checkout UX

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

ويعرض Product total + Delivery per merchant + Grand total قبل التأكيد.

---

# 60. CI/CD & Release Standard

## Pipeline

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

## Branching

```text
main
develop
feature/*
fix/*
release/*
```

يمكن استخدام trunk-based لاحقاً إذا كان الفريق صغيراً وسريعاً.

## Environments

يجب الفصل بين:

```text
Development
Staging
Production
```

ويكون لكل بيئة إعداد Supabase مستقل.

## Release

كل إصدار Production يتطلب:
- release signing.
- AAB.
- R8/ProGuard.
- versioning.
- release notes.
- changelog.

---

# 61. Admin Platform Modernization

لوحة Admin الحالية يمكن الاحتفاظ بها مؤقتاً ثم نقلها تدريجياً إلى Web App حديث مثل:

```text
React / Next.js
+
Supabase
```

أو إطار مناسب وقت التنفيذ.

الوحدات المستهدفة:

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

الصلاحيات الحساسة تبقى دائماً Server-Side.

---

# 62. Future-Ready Platform Interfaces

البنية يجب أن تسمح بإضافة:

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

عبر interfaces قابلة للاستبدال:

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

# 63. Definition of Done

لا تعتبر أي مرحلة مكتملة إلا إذا تحقق:

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

# 64. Versioning & Change Log

## File Versioning Rule

- النسخة الحالية: **tani v1**.
- أول تحديث لاحق: **tani v2**.
- ثم **tani v3** وهكذا.
- لا يعاد استخدام رقم نسخة قديم لملف جديد.

## tani v1 — Changes Applied

تم دمج خطة الصيانة والتحديث الشامل داخل مواصفة المشروع، وتشمل التغييرات الرئيسية:

- تأمين Session/Token storage وإضافة Keystore/DataStore strategy.
- استبدال Password Reset deep link غير الموثق بـ Verified App Links.
- تشديد Supabase RLS/RPC/Storage/Auth/Admin security.
- تقسيم Architecture إلى core/data/domain/feature مع Repository + UseCase + ViewModel boundaries.
- إعادة تصميم Cart/Quote/Checkout وفق Server Authority وAtomic RPC.
- إضافة Idempotency وInventory concurrency وOrder state machine صارمة.
- إضافة Automated Testing standard.
- إضافة Material 3/Design System/Compose migration تدريجية.
- إضافة Low-Bandwidth/Room/Paging/Image caching/Macrobenchmark/Baseline Profiles.
- توسيع Analytics إلى Monitoring/Observability/Operational alerts.
- إضافة CI/CD إنتاجي وStaging/Production separation وSigned AAB/R8.
- تحديد Admin modernization وFuture-ready provider abstractions.
- تشديد Launch Readiness وDefinition of Done.

**End of Document**
