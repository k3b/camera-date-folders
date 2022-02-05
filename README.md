# Camera Date Folders

Android application to automatically sort photos from camera folder to respective date related subfolders

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

# Not Supported (Yet)

* Currently no EXIF metadata are extracted, instead the photo's date must be encoded in the file name.
* More than one photo directory.
* Moving photos to a different device, e.g. to SD card.

# Permissions Needed

* None (permission management is done via SAF only)

# License

The *Camera Date Folders* application is licensed according to GPLv3, see LICENSE file.

# External Licenses

none
