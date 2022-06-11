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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

// the actual work is done here
public class Utils
{
    private static final String LOG_TAG = "CDF : Utils";
    public boolean mustStop = false;
    Context mContext;
    DocumentFile mRootDir;
    int directoryLevel;
    private static final int maxDirectoryLevel = 8;
    private static final int maxFiles = 1000000;    // TODO: remove debug code
    boolean m_SortYear;
    boolean m_SortMonth;
    boolean m_SortDay;
    public ArrayList<mvOp> mOps = null;
    public int mUnchangedFiles;
    private int mFiles;    // TODO: remove debug code
    FindFileCache mFfCache = new FindFileCache();

    // file name holds year, month and day
    public static class camFileDate
    {
        public String year;        // 1999 .. 2999
        public String month;       // 1 .. 12
        public String day;         // 1 .. 31
    }

    // a single move operation
    public static class mvOp
    {
        public String srcPath;              // debug helper
        public DocumentFile srcFile;
        public DocumentFile srcDirectory;
        public String dstPath;
    }

    // progress callback
    interface ProgressCallBack
    {
        void tellProgress(final String text);
    }


    /**************************************************************************
     *
     * constructor
     *
     *************************************************************************/
    Utils(Context context, Uri treeUri, boolean sortYear, boolean sortMonth, boolean sortDay)
    {
        mContext = context;
        mRootDir = DocumentFile.fromTreeUri(mContext, treeUri);
        m_SortYear = sortYear;
        m_SortMonth = sortMonth;
        m_SortDay = sortDay;
    }


    /**************************************************************************
     *
     * Phase 1: gather move operations
     *
     *************************************************************************/
    public int gatherFiles(ProgressCallBack callback)
    {
        mOps = new ArrayList<>();
        mUnchangedFiles = 0;
        mFiles = 0;     // TODO: remove debug code
        directoryLevel = 0;
        if (mRootDir != null)
        {
            gatherDirectory(mRootDir, "", callback);
        }
        return mOps.size();
    }


    /**************************************************************************
     *
     * Phase 2: execute a single move operation
     *
     *************************************************************************/
    @RequiresApi(api = Build.VERSION_CODES.N)
    public boolean mvFile(mvOp op)
    {
        ContentResolver content = mContext.getContentResolver();
        DocumentFile dstDirectory = mRootDir;
        String[] pathFrags = op.dstPath.split("/");
        boolean newDirectory = false;

        for (String frag: pathFrags)
        {
            if (!frag.isEmpty())
            {
                // findFile() is awfully slow. Use LRU cache.
                DocumentFile nextDirectory = mFfCache.findFileCached(dstDirectory, frag);
                if (nextDirectory != null)
                {
                    // Directory already exists? Hopefully it is not a file.
                    if (nextDirectory.isDirectory())
                    {
                        dstDirectory = nextDirectory;
                    }
                    else
                    {
                        Log.e(LOG_TAG, "mvFile() -- is no directory: " + frag);
                        return false;
                    }
                }
                else
                {
                    // Directory does not exist, yet. Create one.
                    nextDirectory = dstDirectory.createDirectory(frag);
                    if (nextDirectory != null)
                    {
                        dstDirectory = nextDirectory;
                        newDirectory = true;
                    }
                    else
                    {
                        Log.e(LOG_TAG, "mvFile() -- cannot create directory: " + frag);
                        return false;
                    }
                }
            }
        }

        try
        {
            Uri newUri = DocumentsContract.moveDocument(content, op.srcFile.getUri(), op.srcDirectory.getUri(), dstDirectory.getUri());
            return newUri != null;
        }
        catch (Exception e)
        {
            Log.e(LOG_TAG, "cannot move file to " + ((newDirectory) ? "new" : "existing") + "directory");
            Log.e(LOG_TAG, "mvFile() -- exception " + e);
        }
        return false;
    }


    /**************************************************************************
     *
     * exception safe string-to-number conversion, returns -1 on failure
     *
     *************************************************************************/
    private int getNumber(final String str)
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
    private boolean isCameraFileType(final String name)
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
    private camFileDate isCameraFile(final String name)
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
     * calculate destination path, depending on tree sort configuration
     *
     *************************************************************************/
    private String getDestPath(camFileDate date)
    {
        String ret = "/";
        if (m_SortYear)
        {
            ret += date.year + "/";
        }
        if (m_SortMonth)
        {
            ret += date.year + "-" + date.month + "/";
        }
        if (m_SortDay)
        {
            ret += date.year + "-" + date.month + "-" + date.day + "/";
        }
        return ret;
    }


    /**************************************************************************
     *
     * recursively walk through tree and gather mv operations to mOps
     *
     *************************************************************************/
    private void gatherDirectory(DocumentFile dd, String path, ProgressCallBack callback)
    {
        Log.d(LOG_TAG, "gatherDirectory() -- ENTER DIRECTORY " + dd.getName());

        if (mustStop)
        {
            Log.d(LOG_TAG, "gatherDirectory() -- stopped");
            return;
        }

        DocumentFile[] entries = dd.listFiles();
        Log.d(LOG_TAG, "gatherDirectory() -- number of files found: " + entries.length);
        for (DocumentFile df: entries)
        {
            if (mustStop)
            {
                return;
            }

            final String name = df.getName();
            if (name == null)
            {
                Log.w(LOG_TAG, "gatherDirectory() -- skip null name");
            }
            else
            if (name.startsWith("."))
            {
                Log.w(LOG_TAG, "gatherDirectory() -- skip dot files: " + name);
            }
            else
            if (df.isDirectory())
            {
                if (directoryLevel < maxDirectoryLevel)
                {
                    directoryLevel++;
                    gatherDirectory(df, path + "/" + name, callback);
                    directoryLevel--;
                }
                else
                {
                    Log.w(LOG_TAG, "gatherDirectory() -- path depth overflow, ignoring " + name);
                }

            }
            else
            {
                mFiles++;
                if (mFiles > maxFiles)
                {
                    Log.w(LOG_TAG, "gatherDirectory() -- DEBUG LIMIT: max number " + maxFiles + " of files exceeded");
                    return;
                }

                if (isCameraFileType(name))
                {
                    camFileDate date = isCameraFile(name);
                    if (date != null)
                    {
                        Log.d(LOG_TAG, "gatherDirectory() -- camera file found: " + path + "/" + name);
                        mvOp op = new mvOp();
                        op.srcPath = path + "/";
                        op.dstPath = getDestPath(date);
                        if (op.srcPath.equals(op.dstPath))
                        {
                            Log.d(LOG_TAG, "   already sorted to its date directory");
                            mUnchangedFiles++;
                        }
                        else
                        {
                            op.srcDirectory = dd;
                            op.srcFile = df;
                            mOps.add(op);
                        }
                    }
                    else
                    {
                        Log.w(LOG_TAG, "gatherDirectory() -- image file does not look like camera file: " + name);
                    }
                    callback.tellProgress("" + mOps.size() + "/" + mUnchangedFiles);
                }
                else
                {
                    Log.w(LOG_TAG, "gatherDirectory() -- non matching file type: " + name);
                }
            }
        }

        Log.d(LOG_TAG, "gatherDirectory() -- LEAVE DIRECTORY " + dd.getName());
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

        // get ISO8601 date instead of dumb US format (Z = time zone) ...
        @SuppressLint("SimpleDateFormat") SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        Date buildDate = new Date(BuildConfig.TIMESTAMP);
        ret.strCreationTime = df.format(buildDate);
        ret.isDebug = BuildConfig.DEBUG;

        return ret;
    }
}
