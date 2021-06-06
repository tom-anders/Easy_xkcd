## 7.3.10

- In preparation for Android 11's scoped storage, at first startup after the update the app will
  move all offline data (comics, articles) to an app-specific folder that's compatible with 
  scoped storage. In a future update, the READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE permissions
  will then be removed.
- Add option to respect system night theme (enabled by default)
- Increase size of Floating Action Button (@mueller-ma)
- Use switches instead of checkboxes in preferences (@mueller-ma)
- Move "Update on mobile data" from "advanced" to "offline" settings (@mueller-ma)
- Color navigation bar based on day/night theme (@mueller-ma)
- Update Brazilian Portuguese translation (@rffontenelle)
- Fix some HTML issues in alt text (@radiohazard-dev)
- Add this year's April Fools comic to interactive comics (@coolreader18)

## 7.3.9

- Add support for comic 2293 (Thanks, @obfusk!)
- Fix What-if equations
- Fix download of images in What-if offline mode

## 7.3.7

- Make widget alt text more readable (Thanks, PeterHindes!)
- Add metadata for F-Droid (Thanks, mueller-ma!)
- Update Brazilian Portuguese translation (Thanks, rffontenelle!)
- Fix What-if images not being displayed
- Fix comic 2281 not being displayed

## 7.3.6

- Fix What If articles not being displayed
- Fix bug where keyboard would constantly appear

## 7.3.5

- Fix comic 2202 
- Tapping an interactive comic will now trigger "Open in Browser"
- Update translations

## 7.3.4

- Fix crash introduced in previous version

## 7.3.3

- Fix for comic 2185
- Add Brazilian Portuguese translation
- Fix encoding of comic 2175 alt text
- Fix option to hide the action bar subtitle

## 7.3.2

- Fix bug where offline comics had black bars at the bottom. 
  You need to disable and then enable offline mode again in the settings for the changes to take effect.
- Add back "Open in Browser" option

## 7.3.1

- Fix color of bullet points in What-If night mode
- Fixed crash caused by widget

## 7.3

- Fixed F-Droid build (hopefully)

## 7.2.1

- Fixed previous random comic in offline mode
- Fixed some comics not being marked as read

## 7.2

- Added dividers in the overflow menu

## 7.1

- Added an option in the settings to reload the database manually

## 7.0.5

- Fixed large comics resolutions
- Small bug fixes

## 7.0

- Notifications now use native Android API and should be much more reliable
- New transitions when entering and leaving overview mode
- Added AMOLED night theme
- Complete rewrite of the App's database
- Added a fullscreen mode that is toggled by tapping a comic (Can be disabled in the settings)
- Missing offline comics can now be downloaded manually directly from the comic browser
- Changed default style for the alt text
- Added a progress dialog while a comic image is loading
- Fixed comic transcripts being off by one or two 

In case you have any issues with this update, let me know: easyxkcd@gmail.com

## 6.1.2

- Added fast scrolling to What-If overview
- Added double tap to favorite (Has to be enabled in settings)
- Fixed duplicate article in What-If offline mode

## 6.1.1

- Added an option to align Floating Action Button left instead of right
- Chrome Custom Tabs can now be disabled in the settings

## 6.1

- Added adaptive icon (Thanks to mueller-ma!)
- Fixed What-if thumbnails!

## 6.0.5

- Added simplified chinese translation (thanks to Lu!)

## 6.0.3

- Added spanish translation (thanks to Giovanni!)
- Option to mark all comics as read in the overview (thanks to GrantM11235!)

## 6.0.2

- Fixed crashes on KitKat!

## 6.0.1

- Fixed crash when sharing comic image

## 5.3.8

- Fixed comic #1826 in offline mode (may have caused crashes for most users)

## 5.3.7

- Fixed Reddit links
- Added missing What-If images

## 5.3.6

- Colors of What-If images can now be inverted too

## 5.3.2

- Adjusted zoom level for large comics
- Added night mode toggle in the overflow menu
- Night mode now won't invert images that contain too much colors
- Added settings for widgets

## 5.3

- Made overview mode accessible from favorites
- Added option to include comic link when sharing an image
- Adjusted some colors in what-if night mode

## 5.2.2

- Fixed a bug where comics would disappear when the device is rotated

## 5.2

- Added two widgets (Random comic, latest comic)
- Added tooltip for long pressing the random button

## 5.1.5

- Individual comics can be marked as read/unread in overview
- Switched to https
- Fixed a bug in offline mode where the latest comics wouldn't be downloaded
- Fixed some crashes

## 5.1.3

- Fixed duplicate what-if article

## 5.1

- New grid layout for overview mode
- Added "mark all as read" to What If
- Various bug fixes

## 5.0.7

- Some fixes for the April Fools Comic (1663)

## 5.0.6

- Added article number to What-if overview

## 5.0.5

- Fixed bug where favorites would appear twice

## 5.0.4

- Smoother animations for overview mode
- Switched to m.reddit.com links

## 5.0.3

- Added 1509 to large comics
- Added toast to let the user know about mouseover text in What-if

## 5.0

- Open the Reddit or xkcd-Forum thread for comics and articles
- Go to earliest unread comic in overview mode
- New Database, search performance improved

## 4.3

- Custom themes

## 4.2

- Favorites Export fixed
- Offline comics/articles load up to 2x faster

## 4.1.1

- Improved What-If notifications
- Some small bugs fixed

## 4.1

- Import/Export favorites

## 4.0.7

- Added latest What-if thumbnail
- Added Comic 930 to large comics

## 4.0.5

- Hide read comics in Overview Mode
- Gif Support (#961 & #1116)

## 4.0.3

- Adjusted image margins according to Material Design
- Fixed large comics 

## 4.0.2

- Added ability to export Favorites list as text (in Advanced settings)
- Added latest What-if thumbnail

## 4.0.1

- Fixed crashes in 4.0

## 4.0

- Comics load noticeably faster in Online Mode
- Improved Overview Mode scrolling performance
- Option to always display Overview Mode at launch
- Only show "Open in browser" when comic is interactive
- Removed screen orientation setting (to buggy right now, will be rewritten)
- Improved Search Mode performance

## 3.9.2

- Added thumbnail for latest What-if, fixed offline download
- Fixed some alt text encoding issues

## 3.9.1

- Offline path can be changed even when offline mode is disabled

## 3.9

- Long press a comic in overview mode to add it as a bookmark

## 3.8.4

- Improved Back Button behavior

## 3.8.3

- Some fixes to overview mode
- Removed "delete all favorites" from overflow menu
- Added #832 to large comics

## 3.8.2

- Fixed some comics
- App is now usable while downloading offline comics

## 3.8

- New overview style added (you can still switch to the old one)

## 3.7 
- Downloading offline comics/articles should be faster and now uses a notification to display progress.
- Fixed a bug where comics would disappear when opening the app from memory
- Performance improvements

## 3.6.1

- Comics are now marked as read in Overview Mode
- Hit the back button in Comic Browser to open Overview Mode!
- Overall image quality improved

## 3.6

- Hit the back button in Comic Browser to open Overview Mode!
- Overall image quality improved

## 3.5.2

- New material icon (finally!)

## 3.3

- New animation added when selecting search results
- Fixed large comics
- Fixed some crashes



