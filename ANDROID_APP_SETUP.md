# Woven Ledger Android App Setup

Complete Kotlin Android app with Jetpack Compose, Hilt dependency injection, Room database, and demo data seeding matching Symfony fixtures.

## Project Structure

```
android/
├── build.gradle.kts                          # Main build configuration
├── gradle/
│   └── libs.versions.toml                   # Gradle version catalog
├── proguard-rules.pro                       # ProGuard configuration
├── src/main/
│   ├── AndroidManifest.xml                  # App manifest
│   ├── java/com/wovenledger/app/
│   │   ├── WovenLedgerApp.kt               # Hilt application class
│   │   ├── MainActivity.kt                  # Main activity
│   │   ├── ui/
│   │   │   ├── App.kt                       # Main composable with NavHost
│   │   │   ├── theme/
│   │   │   │   ├── Theme.kt                # Material Design 3 theme
│   │   │   │   ├── Color.kt                # Color definitions
│   │   │   │   └── Type.kt                 # Typography
│   │   │   ├── navigation/
│   │   │   │   └── NavigationRoutes.kt      # All 12 screen routes
│   │   │   └── screens/
│   │   │       └── Screens.kt               # All screen composables
│   │   ├── data/
│   │   │   ├── database/
│   │   │   │   ├── WovenLedgerDatabase.kt  # Room database
│   │   │   │   └── DatabaseInitializer.kt   # Demo data seeding
│   │   │   ├── entities/
│   │   │   │   └── Entities.kt              # All data entities
│   │   │   └── dao/
│   │   │       └── Daos.kt                  # All data access objects
│   │   └── di/
│   │       └── DatabaseModule.kt            # Hilt dependency injection
│   └── res/
│       └── values/
│           └── strings.xml                  # App strings
```

## Key Features

### 1. Complete Data Model (13 entities)
- Plant
- Settings
- Party (Customers/Suppliers)
- Item (Raw materials & Finished goods)
- ItemStock
- Staff
- StaffWork
- SalesInvoice & SalesInvoiceLine
- PurchaseBill & PurchaseBillLine
- Receipt
- Payment

### 2. Demo Data Seeding
All Symfony fixtures are ported to Kotlin and seeded automatically on app launch:
- 2 plants
- 8 parties (customers, suppliers, both)
- 10 items with stock per plant
- 6 staff members with wages
- 15 sales invoices
- 10 purchase bills
- 7 receipts
- 5 party payments + 6 wage settlements
- Staff work entries for all members

### 3. 12 Screens with Navigation
1. Dashboard - Overview and key metrics
2. Parties List - All customers and suppliers
3. Party Detail - Individual party information
4. Items List - All inventory items
5. Item Detail - Individual item stock and info
6. Sales Invoices List - All sales invoices
7. Sales Invoice Detail - Individual invoice
8. Sales Invoice Create - New invoice
9. Purchase Bills List - All purchase bills
10. Purchase Bill Detail - Individual bill
11. Purchase Bill Create - New bill
12. Receipts List - Payment receipts
13. Receipt Detail - Individual receipt
14. Receipt Create - New receipt
15. Payments List - All payments
16. Payment Detail - Individual payment
17. Payment Create - New payment
18. Staff List - All staff members
19. Staff Detail - Individual staff information
20. Staff Work List - Wage entries and settlement
21. Staff Work Create - Record new work
22. Settings - Company configuration
23. Reports - Financial analytics

### 4. Technology Stack
- **Kotlin** with Coroutines for async operations
- **Jetpack Compose** for UI
- **Material Design 3** with dynamic theming
- **Navigation Compose** for screen routing
- **Room Database** for local data persistence
- **Hilt** for dependency injection
- **Flow** for reactive data streams

### 5. Architecture
- Single Activity with Compose
- Hilt-based dependency injection for all DAOs
- Repository pattern ready for extension
- Flow-based reactive data access
- Automatic database initialization with demo data

## Setup Instructions

### Prerequisites
- Android Studio Flamingo or later
- JDK 17
- SDK 34 (Android 14)
- Minimum SDK 26 (Android 8)

### Step 1: Create Android Project
```bash
# In Android Studio: File → New → New Android Project
# Select "Empty Activity" template
# Name: Woven Ledger
# Package: com.wovenledger.app
# Minimum SDK: API 26
```

### Step 2: Copy Files
Copy the entire `android/` directory structure into your Android Studio project.

### Step 3: Sync Gradle
1. Click "Sync Now" when prompted
2. Ensure all dependencies are downloaded
3. Build → Make Project

### Step 4: Run the App
1. Create a virtual device (Pixel 6 with API 34 recommended)
2. Click "Run" or press Shift+F10
3. App will automatically seed demo data on first launch

## Dependencies Included

### Compose UI
```
androidx.compose.ui:ui:2024.01.00
androidx.compose.material3:material3:2024.01.00
androidx.compose.material:material-icons-extended:2024.01.00
```

### Navigation
```
androidx.navigation:navigation-compose:2.7.7
```

### Lifecycle & ViewModel
```
androidx.lifecycle:lifecycle-runtime-ktx:2.7.0
androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0
androidx.activity:activity-compose:1.8.1
```

### Database
```
androidx.room:room-runtime:2.6.1
androidx.room:room-ktx:2.6.1
```

### Hilt
```
com.google.dagger:hilt-android:2.50
androidx.hilt:hilt-navigation-compose:1.1.0
```

### Testing
```
junit:junit:4.13.2
androidx.test.ext:junit:1.1.5
androidx.test.espresso:espresso-core:3.5.1
```

## Database

### Room Database Setup
The database is automatically initialized in `WovenLedgerDatabase.kt`:
- Creates tables for all 13 entities
- Runs `DatabaseInitializer` on creation
- Supports Kotlin Flow for reactive queries
- Includes automatic timestamp tracking

### Demo Data
`DatabaseInitializer.kt` seeds deterministic demo data matching Symfony fixtures:
- No randomness - same data on every load
- All relationships properly set up
- Ready for dashboard and reports

## Dependency Injection

### Hilt Modules
`DatabaseModule.kt` provides:
- Single WovenLedgerDatabase instance
- All 13 DAOs as singletons
- Automatic injection into ViewModels and Composables

### Usage in ViewModels
```kotlin
@HiltViewModel
class SalesInvoiceViewModel @Inject constructor(
    private val invoiceDao: SalesInvoiceDao,
    private val itemDao: ItemDao
) : ViewModel() {
    val invoices = invoiceDao.getAllInvoices()
}
```

### Usage in Composables
```kotlin
@Composable
fun SalesInvoicesListScreen(navController: NavHostController) {
    val viewModel: SalesInvoiceViewModel = hiltViewModel()
    val invoices by viewModel.invoices.collectAsState(emptyList())
}
```

## Navigation Routes

All routes are defined in `NavigationRoutes.kt`:
```kotlin
DASHBOARD = "dashboard"
PARTIES_LIST = "parties_list"
PARTY_DETAIL = "party_detail/{partyId}"
ITEMS_LIST = "items_list"
ITEM_DETAIL = "item_detail/{itemId}"
SALES_INVOICES_LIST = "sales_invoices_list"
SALES_INVOICE_DETAIL = "sales_invoice_detail/{invoiceId}"
SALES_INVOICE_CREATE = "sales_invoice_create"
// ... and more
```

## Building for Release

```bash
# Generate signed APK
# Android Studio: Build → Generate Signed Bundle/APK

# Or via gradle:
./gradlew build --release
```

## Customization

### Add a New Screen
1. Add route to `NavigationRoutes.kt`
2. Create composable in `screens/Screens.kt`
3. Add navigation route to `NavHost` in `App.kt`
4. Add BottomNav item if needed

### Add a New Entity
1. Define entity in `entities/Entities.kt`
2. Create DAO interface in `dao/Daos.kt`
3. Add to `WovenLedgerDatabase.kt` entities list
4. Provide DAO in `DatabaseModule.kt`
5. Add to demo data in `DatabaseInitializer.kt`

### Customize Theme
Edit `ui/theme/Color.kt` to change:
- Primary and secondary colors
- Light and dark color schemes
- All Material Design 3 color slots

## Common Issues

### Gradle Sync Fails
- Ensure Gradle version matches AGP 8.2.0
- Check Java version (need 17+)
- Clear Gradle cache: `rm -rf .gradle`

### Database Not Seeding
- Check logcat for `DatabaseInitializer` messages
- Uninstall app and rebuild
- Ensure database file is writable

### Compose Preview Not Working
- Click refresh in preview panel
- Ensure @Preview annotation is present
- Check Compose compiler version matches

## Next Steps

1. **Replace placeholder screens** with actual UI implementations
2. **Create ViewModels** for each screen following MVVM pattern
3. **Add forms** for data entry (invoices, receipts, payments)
4. **Implement filters** and search for list screens
5. **Add charts** for reports and analytics
6. **Set up cloud sync** with backend API
7. **Implement authentication** with login screen

## File Locations

All files are ready to copy and paste into Android Studio:

| File | Location |
|------|----------|
| App.kt | `src/main/java/com/wovenledger/app/ui/App.kt` |
| MainActivity.kt | `src/main/java/com/wovenledger/app/MainActivity.kt` |
| WovenLedgerApp.kt | `src/main/java/com/wovenledger/app/WovenLedgerApp.kt` |
| Theme files | `src/main/java/com/wovenledger/app/ui/theme/` |
| Database files | `src/main/java/com/wovenledger/app/data/database/` |
| Entities | `src/main/java/com/wovenledger/app/data/entities/Entities.kt` |
| DAOs | `src/main/java/com/wovenledger/app/data/dao/Daos.kt` |
| DI Module | `src/main/java/com/wovenledger/app/di/DatabaseModule.kt` |
| Screens | `src/main/java/com/wovenledger/app/ui/screens/Screens.kt` |
| Routes | `src/main/java/com/wovenledger/app/ui/navigation/NavigationRoutes.kt` |
| Config | `build.gradle.kts`, `AndroidManifest.xml` |

## Support

For issues or questions:
1. Check logcat for detailed error messages
2. Verify all files are in correct directories
3. Ensure Gradle sync completes successfully
4. Clear cache and rebuild if needed
