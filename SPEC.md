# SATTL Lab Inventory Kiosk App — Build Spec

## 1. Overview and goals

Build a fully offline Android tablet app that tracks the Spectrum Advanced Training Technology Lab's (SATTL) equipment and who has it. The tablet is mounted on the wall outside the lab in kiosk mode. Lab members log in with a name and PIN to browse inventory and check equipment out and back in. The lab manager (admin) adds items and users, controls what can be checked out, and exports data to CSV over USB.

The app must:

- Hold the full inventory of roughly 100 high-value items (SDRs, servers, computer towers, network switches and similar). Growth is under one item per month.
- Record every checkout and check-in: who, when, where the item went, and why.
- Show at any moment which items are checked out and who has them.
- Export everything to CSV on a USB drive, and restore everything from that same export.
- Run with no network connection of any kind.

The inventory process lives entirely inside this app. There is no companion server, website or desktop tool.

## 2. Environment and constraints

The app runs on one generic 10.1-inch Android 15 tablet (octa-core, 12 GB RAM, 64 GB storage), mounted on a wall in landscape orientation.

| Constraint | Requirement |
| --- | --- |
| Network | None. The app must not request the INTERNET permission or make any network call. |
| Data storage | All data in a local database on the tablet's internal storage. |
| Data transfer | CSV files on a USB drive plugged into the tablet (USB-C, OTG). |
| Orientation | Landscape only, locked. |
| Camera / barcode | Not used. Items are found by browsing or typing. |
| Notifications | None. No email, push or sound alerts. |
| Clock | All dates come from the tablet's clock. The admin must set the correct date and time once during setup. |
| Users | About 5 people, including the admin. |
| Maintenance | The lab manager maintains the app after delivery, assisted by a coding agent. Code must be simple and well commented. |

The lab may connect the tablet to an isolated lab network in the future. Design the data layer so a sync feature could be added later, but build no network code now. To prepare for this, every Item, User and Checkout row carries a UUID that is generated once, never changes and is included in the full export (sections 4 and 7.1).

## 3. Users, roles and permissions

There are two roles: Admin (the lab manager, exactly one) and User (everyone else). The admin can also check items out and in like any user.

| Action | User | Admin |
| --- | --- | --- |
| Log in with name + PIN | Yes | Yes |
| Browse and search all items | Yes | Yes |
| See who currently has a checked-out item | Yes | Yes |
| See an item's full checkout history | No | Yes |
| Check out an available item | Yes | Yes |
| Check in an item they checked out | Yes | Yes |
| Check in an item someone else checked out | No | Yes |
| Change their own PIN | Yes | Yes |
| Add, edit or retire items | No | Yes |
| Mark items checkoutable / not checkoutable | No | Yes |
| Add, edit or deactivate users; reset PINs | No | Yes |
| See retired items and deactivated users | No | Yes |
| Export CSV / restore from export | No | Yes |
| Exit kiosk mode | No | Yes |

The admin account is created on the app's first launch (see section 5). There is no way to create a second admin or transfer the admin role in this version.

## 4. Data model

Four tables in a local SQLite database. All timestamps are stored as UTC epoch milliseconds and displayed in the tablet's local time. Records are never hard-deleted.

Item, User and Checkout rows each have two identifiers: `id`, a local integer used for foreign keys on this tablet, and `uuid`, a random UUID assigned when the row is created. The UUID never changes, is unique within its table, and identifies the row across devices for a future sync feature (section 2). AppSettings has no UUID because it is device-local configuration, not shared data.

### Item

| Field | Type | Rules |
| --- | --- | --- |
| id | integer | Primary key, auto-generated |
| uuid | text | Random UUID, set on creation, never changed, unique (section 2) |
| sattlTag | text | Required, unique (case-insensitive, trimmed), any format |
| manufacturer | text | Required |
| modelNumber | text | Required |
| serialNumber | text | Optional (some items may lack one) |
| homeLocation | text | Required. Room number or description, e.g. "Room 214" |
| dateIntoInventory | date | Required. Defaults to today, editable by admin |
| notes | text | Optional, free text |
| isCheckoutable | boolean | Default true |
| isRetired | boolean | Default false |
| createdAt / updatedAt | timestamp | Set automatically |

An item's status is derived, not stored: Retired if isRetired; otherwise Checked out if it has an open checkout; otherwise Not checkoutable if isCheckoutable is false; otherwise Available. Its current location is the open checkout's destination if checked out, otherwise its homeLocation.

### User

| Field | Type | Rules |
| --- | --- | --- |
| id | integer | Primary key |
| uuid | text | Random UUID, set on creation, never changed, unique (section 2) |
| name | text | Required, unique (case-insensitive). Shown in the login dropdown |
| role | enum | ADMIN or USER |
| pinHash / pinSalt | text | Salted hash, never the plain PIN (section 8) |
| mustChangePin | boolean | True after creation or a PIN reset |
| failedAttempts | integer | Reset to 0 on successful login |
| lockedUntil | timestamp | Null unless locked out |
| isActive | boolean | Default true. Inactive users are hidden from login |
| createdAt | timestamp | Set automatically |

### Checkout

One row per checkout. An open checkout has no checkedInAt.

| Field | Type | Rules |
| --- | --- | --- |
| id | integer | Primary key |
| uuid | text | Random UUID, set on creation, never changed, unique (section 2) |
| itemId | integer | Foreign key to Item |
| userId | integer | Foreign key to User. Always the logged-in user; this person is responsible |
| checkoutDate | date | Defaults to today, editable on the form |
| expectedReturnDate | date | Optional, informational only. Must not be before checkoutDate |
| destination | text | Required. Where the item is going |
| reason | text | Required. Why it is being checked out |
| createdAt | timestamp | Exact time the checkout was saved |
| checkedInAt | timestamp | Null while open |
| checkedInByUserId | integer | Who performed the check-in (borrower or admin) |

A database constraint must guarantee at most one open checkout per item.

### AppSettings

Single row: setupComplete (boolean), recoveryCodeHash and salt, lastExportAt (timestamp), keepScreenOn (boolean, default true), schemaVersion (integer).

## 5. Screens and user flows

Every screen except Login and First-time setup requires a logged-in user. A persistent top bar shows the logged-in name and a large Log out button.

### 5.1 First-time setup (runs once)

1. On first launch, the app asks for the admin's name and a new PIN (entered twice).
2. The app generates a 12-character recovery code, shows it once in large type, and asks the admin to write it down. The admin must tick "I have written this down" to continue.
3. The app reminds the admin to confirm the tablet's date and time are correct, then goes to Login.

### 5.2 Login

1. Dropdown of active users, sorted alphabetically.
2. Large on-screen number pad for the PIN (4–6 digits, masked).
3. On success: if mustChangePin is true, go to Change PIN; otherwise go to Inventory.
4. On failure: show "Incorrect PIN" and attempts remaining. After 5 failures that user is locked for 5 minutes, with a visible countdown. When the lock is applied the failure counter resets, so the user gets 5 fresh attempts after the lockout ends.
5. A small "Admin PIN recovery" link opens Recovery (5.10).

### 5.3 Change PIN

User enters a new PIN twice. Forced on first login and after a reset. Also reachable from the top bar any time (requires the current PIN). On a forced change, the new PIN cannot equal the default PIN that was assigned; this check does not apply to a voluntary change. A wrong current PIN on a voluntary change shows an error but does not count toward the login lockout.

### 5.4 Inventory (home screen)

- A search box filters as you type across SATTL tag, manufacturer, model number, serial number and location. "Location" means both the home location and the current location, so searching a room also finds items from that room that are checked out.
- Filter chips: All, Available, Checked out, My checkouts, Not checkoutable. Admin also sees Retired. All shows every item except retired ones, for the admin too; retired items appear only under Retired. Each filter matches the item's derived status, so a not-checkoutable item that is currently out appears under Checked out. Each chip shows how many items it would show.
- A list with one row per item, sorted by SATTL tag: tag, manufacturer, model, serial, current location, and a coloured status badge.
- In the Checked out and My checkouts filters, each row also shows who has it, destination, checkout date and reason.
- An Export this view button (admin only) exports the current filtered, searched list to CSV (section 7).
- Admin only: Add item button, and an Admin menu (Users, Export / Restore, Settings, Exit kiosk).
- Tapping a row opens Item detail.

### 5.5 Item detail

- All item fields and the derived status and current location.
- If checked out: borrower, checkout date, expected return date, destination, reason.
- Check out button: shown only when the item is Available.
- Check in button: shown only when checked out and the current user is the borrower or the admin.
- Admin only: Edit, Retire / Un-retire, Checkoutable toggle, and the full checkout history table (newest first, including who checked each one in).

### 5.6 Check out

1. Form shows the item summary and the borrower (logged-in user, not editable).
2. Fields: checkout date (defaults to today; cannot be in the future, since a future checkout would be a reservation), expected return date (optional), destination (required), reason (required).
3. Confirm saves the checkout, shows a success message for 2 seconds, then logs the user out automatically.

### 5.7 Check in

1. Confirmation dialog: "Return [tag] to [home location]?" When the admin returns someone else's item, the dialog also names the borrower and says the admin will be recorded as checking it in.
2. Confirm closes the checkout, records the time and who checked it in, shows success for 2 seconds (as in 5.6), then logs out automatically.

### 5.8 Add / edit item (admin)

Form with all Item fields. Validation errors appear next to the field. Saving a duplicate SATTL tag is blocked with a clear message.

### 5.9 Manage users (admin)

- List of users with role, status and whether they currently hold items.
- Add user: name and a default PIN typed by the admin. mustChangePin is set to true.
- Edit name, Reset PIN (admin types a new default PIN; mustChangePin set to true; lockout cleared), Deactivate / Reactivate.

### 5.10 Admin PIN recovery

Enter the recovery code, then set a new admin PIN. A new recovery code is generated and shown once, as in setup.

### 5.11 Export / Restore and Settings (admin)

See section 7 for export and restore. Settings holds: Keep screen on (toggle), Change admin PIN, Regenerate recovery code (requires admin PIN), Exit kiosk mode, and an About line with the app version and database schema version.

## 6. Business rules

1. An item can have at most one open checkout. Checking out an item that is not Available is blocked.
2. One item per checkout. There are no kits or multi-item checkouts.
3. The borrower is always the logged-in user.
4. Only the borrower or the admin can check an item in. The check-in records who did it.
5. On check-in the item's location reverts to its homeLocation.
6. There are no due dates, overdue states or reminders. Expected return date is display-only.
7. Marking an item not checkoutable does not affect an existing open checkout. It only blocks future checkouts.
8. An item that is checked out cannot be retired. The admin must check it in first.
9. Retired items keep their full history, are hidden from users, and are visible to the admin under the Retired filter. Un-retiring restores them.
10. A user who currently holds items cannot be deactivated. The admin must check those items in first.
11. Deactivated users keep their history, disappear from the login list, and can be reactivated.
12. The admin account cannot be deactivated.
13. SATTL tags and user names are unique, compared case-insensitively after trimming spaces.
14. Auto-logout: after 2 minutes with no touch, and immediately after a successful checkout or check-in. Show a 15-second "Still there?" warning before the inactivity logout: the warning appears after 1 minute 45 seconds without a touch, and logout happens at exactly 2 minutes.
15. Every write (checkout, check-in, item or user change) is saved immediately in a database transaction. No unsaved state is ever lost to a logout, crash or power loss.

## 7. Export, import and restore

There are two exports: a full backup the app can restore from, and a quick CSV of the current list view. Both are admin-only and are saved through Android's file picker, so the admin can choose the plugged-in USB drive.

### 7.1 Full export (backup)

One ZIP file named `sattl-inventory-YYYY-MM-DD-HHMM.zip`, containing:

| File | Contents |
| --- | --- |
| items.csv | Every item, including retired ones, all fields including uuid |
| checkouts.csv | Every checkout ever, open and closed, including uuid, with item tag and user names alongside the ids so it reads well in Excel |
| users.csv | Every user: uuid, name, role, active flag, mustChangePin, PIN hash and salt (never plain PINs) |
| settings.csv | Recovery code hash and salt, keepScreenOn |
| manifest.json | App version, schema version, export timestamp, row counts per file |

CSV format: UTF-8 with a byte-order mark so Excel opens it correctly, comma-separated, a header row, values quoted where needed, dates as ISO 8601 (`2026-10-01` and `2026-10-01T14:30:00`).

After a successful export, lastExportAt is updated. If the last export is more than 30 days old (or never), the admin sees a banner on the Inventory screen: "Last backup: N days ago. Export now."

### 7.2 Export this view

A single CSV of exactly what the Inventory list is showing (current filter and search). In the Checked out filter it includes the checkout columns: borrower, checkout date, expected return date, destination, reason.

### 7.3 Restore from full export

1. Admin chooses Restore, then picks a ZIP from the USB drive.
2. The app validates the file: manifest present, schema version supported, all CSVs parse, row counts match, references between tables are valid, no duplicate tags or names.
3. If invalid, show what is wrong and change nothing.
4. If valid, show a summary (item, checkout and user counts, export date) and a warning: "This replaces ALL data on this tablet." The admin confirms by entering their current PIN.
5. The app replaces all data in a single transaction, so a failure leaves the old data intact.
6. Everyone is logged out. Users, including the admin, log in with the PINs that were in effect when the backup was taken.

### 7.4 Initial inventory load

The lab manager will enter the ~100 existing items by hand using Add item. No spreadsheet import is required. Restore (7.3) is the only import path.

## 8. Security and PIN handling

The lab has no special compliance requirements, so security only needs to keep casual misuse out.

- PINs are 4 to 6 digits. Store only a salted hash (PBKDF2-HMAC-SHA256, random 16-byte salt per user, at least 100,000 iterations). Never log or display a PIN.
- The recovery code is hashed the same way. It is shown once at creation and never again.
- 5 wrong PINs in a row locks that user for 5 minutes. The lockout survives app restarts. The failure counter resets when the lock is applied (section 5.2).
- No screenshots of PIN entry screens (FLAG_SECURE on those screens).
- The database is in the app's private storage. Database encryption is not required.

## 9. Kiosk mode

Use Android's built-in app pinning (lock task mode without device-owner setup). It needs no factory reset, at the cost of being exitable by someone who knows the Back + Overview button combination.

- On launch, the app calls startLockTask(). The first time, Android asks for confirmation; the admin approves it.
- The admin enables "App pinning" in Android Settings during setup. The agent's setup guide must include these steps.
- Exit kiosk mode (admin, in Settings) calls stopLockTask() after re-entering the admin PIN.
- Screen stays on while the app is in the foreground when Keep screen on is enabled. The tablet is assumed to be on a charger.
- Back button inside the app never leaves the app; on the Inventory screen it does nothing.
- After a reboot, the admin reopens the app, which pins itself again. Auto-launch on boot is not required.

If stronger lockdown is wanted later, the app can be made a device owner via a one-time ADB command after a factory reset. Structure the kiosk code so that switch is small.

## 10. Visual design

Plain, high-contrast and built for fingers on a wall-mounted screen.

- Material 3 with a neutral light theme and one blue accent colour. No logo or branding.
- Status badge colours: Available green, Checked out amber, Not checkoutable grey, Retired dark grey with strikethrough tag. Each badge also shows its word, so colour is never the only signal.
- Touch targets at least 56 dp; body text at least 18 sp; PIN pad keys at least 72 dp.
- Landscape layout. The Inventory list uses the full width with readable columns. Item detail can use a two-pane layout (fields left, checkout info or history right).
- Every destructive or important action (check in, retire, deactivate, reset PIN, restore) has a confirmation dialog.
- Error messages say what went wrong and what to do, in plain language.

## 11. Tech stack

| Area | Choice |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Database | Room (SQLite), with explicit migrations from version 1 onward |
| Architecture | Single-activity, MVVM (ViewModels + repository layer), Kotlin coroutines and Flow |
| Navigation | Navigation Compose |
| Files / USB | Storage Access Framework (ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT). No storage permissions needed |
| CSV | A small hand-written RFC 4180 reader/writer, or a well-known library such as kotlin-csv |
| ZIP | java.util.zip |
| Hashing | javax.crypto PBKDF2WithHmacSHA256 |
| SDK levels | minSdk 29 (Android 10), targetSdk and compileSdk 35 (Android 15) |
| Permissions | None beyond defaults. Explicitly no INTERNET permission |
| Build | Gradle with Android Studio. Output a signed release APK for sideloading |
| Tests | JUnit unit tests for business rules and CSV round-trip; Room in-memory DB tests |

Keep third-party dependencies to a minimum so the app stays easy to maintain.

## 12. Acceptance criteria

The app is done when every item below passes on the actual tablet.

- [ ] First launch creates the admin account and shows a recovery code once.
- [ ] Admin can add, edit, retire and un-retire items; duplicate SATTL tags are rejected.
- [ ] Admin can add users with a default PIN; each user is forced to set a new PIN on first login.
- [ ] Wrong PIN 5 times locks that user for 5 minutes, even across an app restart.
- [ ] Search finds items by tag, manufacturer, model, serial and location.
- [ ] Each filter (All, Available, Checked out, My checkouts, Not checkoutable, Retired) shows exactly the right items.
- [ ] A user can check out an Available item; the item then shows as Checked out with borrower, destination and reason.
- [ ] A not-checkoutable item cannot be checked out by anyone.
- [ ] A user cannot check in an item someone else borrowed; the admin can.
- [ ] Check-in returns the item to its home location and is recorded in history with who checked it in.
- [ ] Users see who holds an item but not its full history; the admin sees full history.
- [ ] The app logs out after 2 minutes idle and right after each checkout or check-in.
- [ ] Full export writes a ZIP to a USB drive; its CSVs open correctly in Excel.
- [ ] Restoring that ZIP on a freshly installed copy of the app reproduces every item, checkout and user exactly, and everyone can log in with their existing PINs.
- [ ] Restoring a corrupted or wrong file changes nothing and explains the problem.
- [ ] Export this view produces a CSV matching the on-screen list, with checkout details in the Checked out filter.
- [ ] Admin PIN recovery works with the recovery code.
- [ ] Kiosk mode keeps users inside the app; the admin can exit it from Settings.
- [ ] The app works with Wi-Fi and Bluetooth off and requests no network permission.
- [ ] All data survives the tablet being powered off and on.

## 13. Out of scope

Do not build any of the following: consumables or quantity tracking, kits or accessories, barcode/QR/NFC scanning, reservations, due dates or overdue alerts, notifications of any kind, approval workflows, condition or damage reporting, calibration or maintenance tracking, photos, multiple admins, network sync, cloud backup, or a spreadsheet import of the starting inventory.

## 14. Instructions for the coding agent

Build in these milestones, and get each one working and tested before starting the next:

1. Project skeleton, Room database with all four tables, first-time setup, login, Change PIN, auto-logout.
2. Inventory list, search, filters, item detail, add/edit/retire items.
3. Check out and check in with all rules in section 6.
4. User management and admin PIN recovery.
5. Full export, Export this view, and restore, with a round-trip test (export, wipe, restore, compare).
6. Kiosk mode, settings, visual polish.

Also deliver:

- A README with step-by-step instructions, written for a non-developer: installing Android Studio, building the APK, enabling developer options and "Install unknown apps" on the tablet, installing the APK over USB, enabling app pinning, first-time setup, and updating the app later without losing data.
- A note in the README that updates must be signed with the same key, and where that key is stored. Losing it means a future update requires uninstalling, which erases data unless an export is restored afterwards.
- Clear comments on every business rule in code, referencing the section number in this spec.

If anything in this spec is ambiguous or conflicts, ask the lab manager rather than guessing.

## 15. Change log

This spec is kept in sync with the app. When a decision changes or an ambiguity is resolved, the relevant section above is updated and the change is recorded here.

| Date | Change | Sections |
| --- | --- | --- |
| 2026-10-01 | Item, User and Checkout rows get a permanent UUID for future sync; included in the full export. AppSettings is excluded as device-local. | 2, 4, 7.1 |
| 2026-10-01 | After a lockout, the failure counter resets, giving 5 fresh attempts. | 5.2, 8 |
| 2026-10-01 | "New PIN ≠ default PIN" applies only to a forced change. A wrong current PIN on a voluntary change does not count toward the lockout. | 5.3 |
| 2026-10-01 | The idle warning appears at 1:45, so logout happens at exactly 2:00 of inactivity. | 6.14 |
| 2026-10-01 | Search matches both home and current location. Confirmed by the lab manager. | 5.4 |
| 2026-10-01 | All excludes retired items for the admin too, and filters follow derived status, so a not-checkoutable item that is out shows under Checked out. Confirmed by the lab manager. | 5.4 |
| 2026-10-01 | Checkout details also show in the My checkouts filter, not just Checked out. Confirmed by the lab manager. | 5.4 |
| 2026-10-07 | The checkout date cannot be in the future (a future date would be a reservation, which section 13 rules out). Confirmed by the lab manager. | 5.6 |
| 2026-10-07 | The check-in success message shows for 2 seconds, as for checkout. When the admin returns someone else's item, the dialog names the borrower. | 5.7 |
