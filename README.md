
#  SocialApp — کلاینت اندروید شبکه اجتماعی
یک اپلیکیشن پیام‌رسان کامل و مدرن برای اندروید که با **Kotlin** و **Android Studio** توسعه یافته است. این کلاینت بخشی از یک سیستم بزرگ‌تر شبکه اجتماعی است که سرور آن با **FastAPI (Python)** پیاده‌سازی شده و پنل‌های مدیریت وب و دسکتاپ نیز برای آن توسعه داده شده‌اند.

##  امکانات

###  احراز هویت
- ثبت‌نام و ورود با **شماره تلفن** و کد یکبارمصرف (OTP)
- دریافت کد فعال‌سازی از پنل مدیریت (به انتخاب مدیر سرور)
- ذخیره‌ی نشست در `SharedPreferences` — کاربر تا زمانی که خارج نشود، وارد باقی می‌ماند
- احراز هویت با **JWT** (JSON Web Token)

###  پیام‌رسانی
- **چت خصوصی** بین دو کاربر
- **گروه** با ظرفیت بالا و مدیریت اعضا (مالک، ادمین، عضو)
- **کانال** با محدودیت ارسال پیام فقط برای ادمین‌ها
- ارسال پیام متنی با قابلیت‌های:
  - **پاسخ / نقل‌قول** با پیش‌نمایش پیام اصلی
  - **ویرایش** پیام (تا زمانیکه حذف نشده)
  - **حذف برای همه** (طرف مقابل پیام را نمی‌بیند)
  - **حذف فقط برای من** (طرف مقابل همچنان می‌بیند)
  - **کپی** متن پیام
- **جستجو** در تاریخچه‌ی پیام‌های چت
- **صفحه‌بندی (Pagination)** — بارگذاری پیام‌های قدیمی‌تر با اسکرول به بالا

###  رسانه و فایل
- ارسال **عکس** با نمایش در حباب پیام (بارگذاری با Coil)
- ارسال **فایل** با کارت مخصوص (نام، حجم، دانلود)
- ضبط و ارسال **پیام صوتی** (نگه داشتن دکمه‌ی میکروفون)
- پخش پیام صوتی با `MediaPlayer`
- آپلود **آواتار** از گالری

###  تماس صوتی و تصویری
- **تماس صوتی** با WebRTC
- **تماس تصویری** با WebRTC (وضعیت: در حال تکمیل پیش‌نمایش تصویر محلی)
- اعلان تماس ورودی با دیالوگ پذیرش/رد

###  مدیریت گروه و کانال
- ساخت گروه و کانال از داخل اپ
- **افزودن عضو** با شماره تلفن
- **حذف عضو** توسط مالک یا ادمین
- **تغییر نقش** (مالک / ادمین / عضو) توسط مالک
- مشاهده‌ی وضعیت **آنلاین/آفلاین** اعضا

###  سازماندهی
- **پوشه‌بندی چت‌ها (Folders)** — دسته‌بندی چت‌ها در پوشه‌های مجزا (کاری، خانوادگی و...)
- فیلتر کردن لیست چت‌ها بر اساس پوشه
- ساخت، تغییر نام و حذف پوشه

###  وضعیت و اعلان
- نمایش **وضعیت آنلاین** (نقطه‌ی سبز)
- نمایش **آخرین بازدید** (مثلاً «۵ دقیقه پیش»)
- **تیک خوانده‌شدن** (✓ و ✓✓) برای پیام‌های ارسالی
- **پیام همگانی** از طرف «مدیریت» به همه یا چند کاربر انتخابی

###  رابط کاربری
- طراحی **راست‌چین (RTL)** و کاملاً فارسی
- استفاده از **Material Design 3**
- تم روشن و رنگ‌بندی حرفه‌ای
- نمایش حالت خالی (empty state) هنگام نبود داده


##  ساختار پروژه

```
app/src/main/java/com/example/socialapp/
├── api/                    # لایه‌ی ارتباط با سرور (REST)
│   ├── Models.kt           #   (UserOut, MessageOut, ...)
│   ├── SocialApi.kt        #  Retrofit
│   └── RetrofitClient.kt   #  Retrofit + OkHttp
├── data/
│   └── SessionManager.kt   # مدیریت توکن   
├── ui/                     # لایه‌ی رابط کاربری
│   ├── LoginActivity.kt    # ورود با شماره تلفن
│   ├── OtpActivity.kt      # تأیید کد OTP
│   ├── ChatListActivity.kt #  چت‌ها + پوشه‌ها
│   ├── ChatActivity.kt     # صفحه‌ی چت
│   ├── CallActivity.kt     # صفحه‌ی تماس WebRTC
│   ├── ChatListAdapter.kt  # آداپتر لیست چت‌ها
│   ├── MessageAdapter.kt   # آداپتر پیام‌ها
│   └── GroupMembersDialog.kt # دیالوگ مدیریت اعضا
├── util/
│   └── Constants.kt        #  (BASE_URL, WS_URL, ...)
├── webrtc/
│   └── WebRTCClient.kt     #  WebRTC
└── ws/
    └── WebSocketManager.kt #   WebSocket
```

## راه‌اندازی

### پیش‌نیازها

- **Android Studio** Hedgehog (2023.1.1) یا جدیدتر
- **JDK 17** یا بالاتر
- **Android SDK** 34
- **Gradle** 8.0+

### گام ۱: کلون کردن پروژه

git clone https://github.com/akhzari-itshield/SocialApp-Client

cd SocialApp

### گام ۲: تنظیم آدرس سرور
```

فایل `app/src/main/java/com/example/socialapp/util/Constants.kt` را باز کنید و آدرس سرور را تنظیم کنید:

object Constants {    
    //  IP کامپیوتر خود را بگذارید (مثال):
    // const val BASE_URL = "http://192.168.1.10:8000/"
    // const val WS_URL = "ws://192.168.1.10:8000/ws"
   
}
```

### گام ۳: Build و Run
# در Android Studio:
1. File → Sync Project with Gradle Files
2. Build → Clean Project
3. Build → Rebuild Project
4. Run → Run 'app'
##  تنظیمات مورد نیاز
### مجوزهای `AndroidManifest.xml`

```
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```
### فعال بودن Cleartext (برای HTTP بدون SSL)

در `AndroidManifest.xml` در تگ `<application>`:


android:usesCleartextTraffic="true"

>  **هشدار:** این گزینه فقط برای توسعه است. برای production باید HTTPS راه‌اندازی کنید و این خط را حذف کنید.

### تست اولیه (بدون گوشی دوم)
1. سرور را روی کامپیوتر اجرا کنید
2. اپ را در شبیه‌ساز باز کنید
3. با شماره تلفن وارد شوید — کد OTP از پنل مدیریت بگیرید
4. وارد لیست چت‌ها می‌شوید (احتمالاً خالی)

### تست کامل (دو گوشی)
1. روی گوشی A و B اپ را نصب کنید
2. هر دو با شماره‌ی متفاوت وارد شوید
3. یک چت خصوصی بسازید
4. پیام متنی، عکس، فایل و پیام صوتی بفرستید
5. تماس صوتی و تصویری برقرار کنید
6. گروه و کانال بسازید و عضو اضافه کنید

### ** اگر این پروژه برایتان مفید بود، ستاره بدهید! **
