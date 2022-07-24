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
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

// the actual work is done here
public class Utils
{
    private static final String LOG_TAG = "CDF : Utils";
    public boolean mustStop = false;
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
    protected FindFileCache mFfCache = new FindFileCache();

    // file name holds year, month and day
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
     * Phase 1: gather move operations
     *
     *************************************************************************/
    public void gatherFiles(ProgressCallBack callback)
    {
        mOps = new ArrayList<>();
        mFilesInDest = new HashSet<>();     // hash is faster than ArrayList when searching
        mUnchangedFiles = 0;
        mFiles = 0;     // TODO: remove debug code
        directoryLevel = 0;
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
            final String path2 = uri2.getPath();
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
        // check file name extension
        //

        return (name.endsWith(".mp4") ||
                name.endsWith(".3gp") ||
                name.endsWith(".jpg") ||
                name.endsWith(".jpeg"));
    }


    /**************************************************************************
     *
     * heuristic method to decide if a file is a photo or a movie taken with
     * the camera
     *
     *************************************************************************/
    protected camFileDate isCameraFile(final String name)
    {
        //
        // skip prefix containing of non-digit characters
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
        //

        char c = name.charAt(i + 8);
        if (Character.isDigit(c))
        {
            return null;
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
}
