# Camera Date Folders

Android application to automatically sort photos from camera folder to respective date related subfolders

***

<a href='https://play.google.com/store/apps/details?id=de.kromke.andreas.cameradatefolders'><img src='public/google-play.png' alt='Get it on Google Play' height=45/></a>
<a href='https://f-droid.org/packages/de.kromke.andreas.cameradatefolders'><img src='public/f-droid.png' alt='Get it on F-Droid' height=45 ></a>

***

## The Challenge

While standalone cameras usually sort photos taken to specific folders on the SD card, Android photo programs do not do so. Instead, thousands of photos are stored in the same directory, usually *DCIM/Camera*. When the photos are copied via USB to a computer, the transfer often is unstable and timeouts while reading the huge directory. Further, it is difficult to copy newer pictures, e.g. those taken last month.

## The Solution

This program creates a folder tree structure and sorts the photos into these folders. As a result, no more than photos taken on one day or month shall be stored in the same directory. As a consequence it it will become trivial to copy a day or month folder from the device to the computer.

# Supported

* One, two or three levels of subfolders.
* Daily, monthly or yearly subfolders.
* Revert, i.e. all files are moved back from subfolders to main folder (flattening).
* File names beginning with "yyyymmdd\_".
* File names beginning with "PXL\_yyyymmdd_" or "IMG\_yyyymmdd\_".
* File name extensions ".jpg", ".jpeg" and ".mp4".

# Current Limitations (Yet)

* Currently no EXIF metadata are extracted, instead the photo's date must be encoded in the file name.
* Only one photo directory is currently supported.
* Photos currently cannot be moved to a different device, e.g. to SD card. Instead they remain in the same directory subtree.
* Due to Google's policy, the program is forced to use Google's Storage Access Framework, which is extremely slow. A faster version is technically trivial, but that one would not be allowed to be published in Play Store.
* The Storage Access Framework does not support file move operations for Android 6 and older. Currently there is no workaround implemented.

# Permissions Needed

* None (permission management is done via SAF only)

# License

The *Camera Date Folders* application is licensed according to GPLv3, see LICENSE file.

# External Licenses

none
