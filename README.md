# Camera Date Folders

Android application to automatically sort photos from camera folder to respective date related subfolders

***

<a href='https://play.google.com/store/apps/details?id=de.kromke.andreas.cameradatefolders'><img src='public/google-play.png' alt='Get it on Google Play' height=45/></a>
<a href='https://f-droid.org/packages/de.kromke.andreas.cameradatefolders'><img src='public/f-droid.png' alt='Get it on F-Droid' height=45 ></a>

***

## The Challenge

While camera devices usually sort photos taken to specific folders on the SD card, Android photo programs do not do so. Instead, thousands of photos are stored in the same directory, usually *DCIM/Camera*. When the photos are copied via USB to a computer, the transfer often is unstable and timeouts while reading the huge directory. Further, it is difficult to copy newer pictures, e.g. those taken last month.

## The Solution

This program creates a folder tree structure and sorts the photos into these folders. As a result, no more than photos taken on one day or month shall be stored in the same directory. As a consequence it it will become trivial to copy a day or month folder from the device to the computer.

Even more: Given a Document Provider (like the "CIFS Documents Provider" or some cloud services, but not Google) supporting directories, the application can directly make incremental backups e.g. on a computer in the same LAN.

# Supported

* One, two or three levels of subfolders.
* Daily, monthly or yearly subfolders.
* Revert, i.e. all files are moved back from subfolders to main folder (flattening).
* Keep photos within camera folder path or specify separate destination directory.
* Either move or copy (incremental backup) photos to separate destination directory.
* In SAF mode the destination could also be a shared folder on a computer (needs a Document Provider app on the device).
* Deselect destination directory by leaving the file selector with the BACK button or set as same as camera folder.
* Already sorted photos will be re-sorted when subfolder scheme was changed, also in destination folder (if any).
* File names beginning with "yyyymmdd\_".
* File names beginning with "PXL\_yyyymmdd_" or "IMG\_yyyymmdd\_".
* File name extensions ".jpg", ".jpeg" and ".mp4".
* Storage Access Framework used for Android 7 and newer.
* Traditional File mode used for Android 4.4, 5 and 6, as SAF does not support file move operations.
* Fixed camera path "DCIM/Camera" for Android 4.4 due to lack of file selector.
* Traditional File mode can be forced for Android 7 to 10 for higher speed, but due to Android without write permission to SD card.

# Current Limitations (Yet)

* Currently no EXIF metadata are extracted, instead the photo's date must be encoded in the file name.
* Only one photo directory is currently supported.
* Due to Google's policy, the program is forced to use Google's Storage Access Framework, which is extremely slow. A faster version is technically trivial, but that one would not be allowed to be published in Play Store.

# Permissions Needed

* For Android 4.4, 5 and 6: Storage write access
* For Android 7 and newer: Storage write access only needed on demand, if File mode is forced

# Technical Procedere

Without destination path:

1. Scan photos and remember those that must be moved, including demanded relative path.
2. Create all necessary subfolders.
3. Process all remembered move operations.
4. Scan photo directory to remove empty date related subfolders.

With destination path:

1. Scan photos in destination path and remember them.
2. And remember those that must be moved, including demanded relative path.
3. Scan photos in source path and remember those that are not present in destination path, including demanded relative path in destination.
4. Create all necessary subfolders in destination path.
5. Process all remembered move operations in destination path.
6. Process all remembered move or copy operations from source to destination path.
7. Scan destination directory to remove empty date related subfolders.

# License

The *Camera Date Folders* application is licensed according to GPLv3, see LICENSE file.

# External Licenses

none
