/*
 * Copyright (C) 2022 Andreas Kromke, andreas.kromke@gmail.com
 *
 * This program is free software; you can redistribute it or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package de.kromke.andreas.cameradatefolders;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;


/** @noinspection JavadocBlankLines*/ // the actual work is done here
public class Utils
{
    private static final String LOG_TAG = "CDF : Utils";
    public boolean mustStop = false;
    public int mErrCode = 0;
    protected Context mContext;
    protected boolean mbBackupCopy;
    protected boolean mbDryRun;
    protected int directoryLevel;
    protected static final int maxDirectoryLevel = 8;
    protected static final int maxFiles = 1000000;    // TODO: remove debug code
    protected boolean mSortYear;
    protected boolean mSortMonth;
    protected boolean mSortDay;
    public ArrayList<mvOp> mOps = null;
    protected Set<String> mFilesInDest = null;    // list of photo files in destination directory

    public int mUnchangedFiles;
    public int mRemovedDirectories;
    protected int mFiles;    // TODO: remove debug code
    protected int mEmptyDateDirs;
    protected int mIgnoredImageFiles;
    protected FindFileCache mFfCache = new FindFileCache();

    protected int mMoveFileFailures = 0;
    protected int mCopyFileFailures = 0;
    protected int mMkdirSuccesses = 0;
    protected int mMkdirFailures = 0;
    protected int mRmdirSuccesses = 0;
    protected int mRmdirFailures = 0;

    // filename holds year, month and day
    public static class camFileDate
    {
        public String year;        // 1999 .. 2999
        public String month;       // 1 .. 12
        public String day;         // 1 .. 31
    }

    public interface mvOp
    {
        String getName();
        String getSrcPath();
        String getDstPath();
        boolean move();
    }


    // progress callback
    interface ProgressCallBack
    {
        void tellProgress(final String text);
    }


    /**************************************************************************
     *
     * constructor, also chooses between File and SAF mode
     *
     *************************************************************************/
    Utils(Context context, boolean backupCopy, boolean dryRun, boolean sortYear, boolean sortMonth, boolean sortDay)
    {
        mContext = context;
        mbBackupCopy = backupCopy;
        mbDryRun = dryRun;
        mSortYear = sortYear;
        mSortMonth = sortMonth;
        mSortDay = sortDay;
    }


    public ArrayList<mvOp> getOps()
    {
        return mOps;
    }


    /**************************************************************************
     *
     * Phase 1a: gather move operations for destination path, if any
     *
     *************************************************************************/
    public int gatherFilesDst(ProgressCallBack callback)
    {
        mOps = new ArrayList<>();
        mFilesInDest = new HashSet<>();     // hash is faster than ArrayList when searching
        mUnchangedFiles = 0;
        mFiles = 0;     // TODO: remove debug code
        directoryLevel = 0;

        mEmptyDateDirs = 0;
        mIgnoredImageFiles = 0;
        mMoveFileFailures = 0;
        mCopyFileFailures = 0;
        mMkdirSuccesses = 0;
        mMkdirFailures = 0;
        mRmdirSuccesses = 0;
        mRmdirFailures = 0;

        return 0;
    }


    /**************************************************************************
     *
     * Phase 1b: gather move operations for source path
     *
     *************************************************************************/
    public int gatherFilesSrc(ProgressCallBack callback)
    {
        directoryLevel = 0;
        mEmptyDateDirs = 0;
        mIgnoredImageFiles = 0;
        return 0;
    }


    /**************************************************************************
     *
     * Phase 2: remove empty directories that look like date/time related
     *
     *************************************************************************/
    public void removeUnusedDateFolders(ProgressCallBack callback)
    {
        mRemovedDirectories = 0;
        directoryLevel = 0;
    }


    /**************************************************************************
     *
     * (Phase 1): check if file must be moved/copied in case path does not match
     *
     * path is only for log
     *
     *************************************************************************/
    protected boolean mustBeProcessed(final String name, final String path, boolean bIsInDestDir)
    {
        if (bIsInDestDir)
        {
            // gather filenames in destination directory
            Log.d(LOG_TAG, "gatherDirectory() -- camera file found in dest: " + path + "/" + name);
            mFilesInDest.add(name);
        }
        else
        if (mFilesInDest != null)
        {
            // check if file is already stored in destination
            if (mFilesInDest.contains(name))
            {
                Log.d(LOG_TAG, "gatherDirectory() -- old camera file found: " + path + "/" + name);
                return false;
            } else
            {
                Log.d(LOG_TAG, "gatherDirectory() -- new camera file found: " + path + "/" + name);
            }
        } else
        {
            Log.d(LOG_TAG, "gatherDirectory() -- camera file found: " + path + "/" + name);
        }
        return true;
    }


    /**************************************************************************
     *
     * check if these two paths overlap
     *
     *************************************************************************/
    public static boolean pathsOverlap(final Uri uri1, final Uri uri2)
    {
        if ((uri1 != null) && (uri2 != null))
        {
            final String path1 = uri1.getPath();
            if (path1 == null)
            {
                Log.e(LOG_TAG, "pathsOverlap() -- invalid Uri: " + uri1);
                return false;
            }

            final String path2 = uri2.getPath();
            if (path2 == null)
            {
                Log.e(LOG_TAG, "pathsOverlap() -- invalid Uri: " + uri2);
                return false;
            }

            if (path2.startsWith(path1) || path1.startsWith(path2))
            {
                Log.e(LOG_TAG, "pathsOverlap() -- paths may not overlap: " + path1 + " and " + path2);
                return true;
            }
        }

        return false;
    }


    /**************************************************************************
     *
     * exception safe string-to-number conversion, returns -1 on failure
     *
     *************************************************************************/
    protected int getNumber(final String str)
    {
        try
        {
            return Integer.parseInt(str);
        }
        catch(Exception e)
        {
            return -1;
        }
    }


    /**************************************************************************
     *
     * checks for image and movie file types, might be incomplete
     *
     *************************************************************************/
    protected boolean isCameraFileType(final String name)
    {
        //
        // check filename extension
        //

        return (name.endsWith(".mp4") ||
                name.endsWith(".3gp") ||
                name.endsWith(".heif") ||
                name.endsWith(".heic") ||
                name.endsWith(".dng") ||
                name.endsWith(".png") ||
                name.endsWith(".jpg") ||
                name.endsWith(".jpeg"));
    }


    /**************************************************************************
     *
     * heuristic method to decide if a file is a photo or a movie taken with
     * the camera
     *
     * Scheme:    [non-digits][yyyymmdd][non-digit][*]
     *         or [non-digits][yyyymmdd][hhmmss][*]
     *
     *************************************************************************/
    protected camFileDate isCameraFile(final String name)
    {
        //
        // skip prefix consisting of non-digit characters
        //

        int i;
        for (i = 0; i < name.length(); i++)
        {
            char c = name.charAt(i);
            if (Character.isDigit(c))
            {
                break;
            }
        }

        //
        // prefix must be followed by 8 digits
        //  yyyymmdd
        // at positions i..i+7
        //

        if (i >= name.length() - 8)
        {
            // no decimal digit found
            return null;
        }

        for (int j = 1; j < 8; j++)
        {
            char c = name.charAt(i + j);
            if (!Character.isDigit(c))
            {
                return null;
            }
        }

        //
        // 8 digits must be followed by non-digit
        //  or another 6 digits (hhmmss). The latter
        //  scheme is used by OnePlus Nord2 5g.
        //

        char c = name.charAt(i + 8);
        if (Character.isDigit(c))
        {
            if (i + 8 + 5 >= name.length())
            {
                // name is not long enough to contain another 5 digits
                return null;
            }

            for (int j = 1; j < 6; j++)
            {
                c = name.charAt(i + 8 + j);
                if (!Character.isDigit(c))
                {
                    return null;
                }
            }
        }

        //
        // get year
        //

        camFileDate ret = new camFileDate();
        ret.year = name.substring(i, i + 4);
        int year = getNumber(ret.year);
        if ((year < 1999) || (year > 2999))
        {
            return null;
        }

        //
        // get month
        //

        ret.month = name.substring(i + 4, i + 6);
        int month = getNumber(ret.month);
        if ((month < 1) || (month > 12))
        {
            return null;
        }

        //
        // get day
        //

        ret.day = name.substring(i + 6, i + 8);
        int day = getNumber(ret.day);
        if ((day < 1) || (day > 31))
        {
            return null;
        }

        return ret;
    }


    /**************************************************************************
     *
     * heuristic method to decide if a directory is date related
     *
     * name scheme is yyyy or yyyy-mm or yyyy-mm-dd
     *
     *************************************************************************/
    protected boolean isDateDirectory(final String name)
    {
        int len = name.length();
        if (len > 10)
        {
            return false;
        }
        int i;
        for (i = 0; i < len; i++)
        {
            char c = name.charAt(i);
            if ((i == 4) || (i == 7))
            {
                if (c != '-')
                {
                    return false;
                }
            }
            else
            if (!Character.isDigit(c))
            {
                return false;
            }
        }

        return true;
    }


    /**************************************************************************
     *
     * calculate destination path, depending on tree sort configuration
     *
     *************************************************************************/
    protected String getDestPath(camFileDate date)
    {
        String ret = "/";
        if (mSortYear)
        {
            ret += date.year + "/";
        }
        if (mSortMonth)
        {
            ret += date.year + "-" + date.month + "/";
        }
        if (mSortDay)
        {
            ret += date.year + "-" + date.month + "-" + date.day + "/";
        }
        return ret;
    }


    /**************************************************************************
     *
     * helper
     *
     *************************************************************************/
    protected static void closeStream(Closeable s)
    {
        try
        {
            s.close();
        }
        catch (Exception e)
        {
            Log.e(LOG_TAG, "I/O exception");
        }
    }


    /**************************************************************************
     *
     * helper
     *
     *************************************************************************/
    protected static boolean copyStream(InputStream is, OutputStream os)
    {
        boolean result = true;
        try
        {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = is.read(buffer)) > 0)
            {
                os.write(buffer, 0, length);
            }
        } catch (FileNotFoundException e)
        {
            Log.e(LOG_TAG, "file not found");
            result = false;
        } catch (IOException e)
        {
            Log.e(LOG_TAG, "I/O exception");
            result = false;
        } finally
        {
            if (is != null)
                closeStream(is);
            if (os != null)
                closeStream(os);
        }

        return result;
    }


    /**************************************************************************
     *
     * user readable version info for the Info fragment
     *
     *************************************************************************/
    public static class AppVersionInfo
    {
        public String versionName = "";
        public int versionCode = 0;
        public String strCreationTime = "";
        public boolean isDebug;
    }


    /**************************************************************************
     *
     * Helper for "About" page
     *
     *************************************************************************/
    public static AppVersionInfo getVersionInfo(Context context)
    {
        AppVersionInfo ret = new AppVersionInfo();
        PackageInfo packageinfo = null;

        try
        {
            packageinfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        }
        catch (PackageManager.NameNotFoundException e)
        {
            Log.d(LOG_TAG, "getVersionInfo() : " + e);
        }

        if (packageinfo != null)
        {
            ret.versionName = packageinfo.versionName;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            {
                ret.versionCode = (int) packageinfo.getLongVersionCode();
            }
            else
            {
                // deprecated in API 29
                ret.versionCode = packageinfo.versionCode;
            }
        }

        // get ISO8601 date instead of impractical US format (Z = time zone) ...
        @SuppressLint("SimpleDateFormat") SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        Date buildDate = new Date(BuildConfig.TIMESTAMP);
        ret.strCreationTime = df.format(buildDate);
        ret.isDebug = BuildConfig.DEBUG;

        return ret;
    }


    /**************************************************************************
     *
     * convert milliseconds as human readable string
     *
     *************************************************************************/
    public static String getTimeStr(long ms)
    {
        String stime = "";

        if (ms < 3000)
        {
            stime = "" + ms + " ms";
        }
        else
        {
            ms /= 1000;       // -> seconds

            long h = ms / 3600;
            ms %= 3600;

            if (h > 0)
            {
                stime = "" + h + 'h';
            }

            long m = ms / 60;
            ms %= 60;

            if (m > 0)
            {
                stime += "" + m + '\'';
            }
            stime += "" + ms + "''";
        }

        return stime;
    }
}
