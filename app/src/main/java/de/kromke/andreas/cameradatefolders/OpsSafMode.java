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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

// the actual work is done here
public class OpsSafMode extends Utils
{
    private static final String LOG_TAG = "CDF : OpsSaf";
    private final DocumentFile mRootDir;      // photo directory in proprietary SAF mode
    private final DocumentFile mDestDir;      // maybe null

    // a single move operation in SAF mode
    public class mvOpSaf implements mvOp
    {
        public DocumentFile srcFile;
        public DocumentFile srcDirectory;
        public String dstPath;              // relative to photo directory
        public String srcPath;              // debug helper

        public String getName()
        {
            return srcFile.getName();
        }
        public String getSrcPath()
        {
            return srcPath;
        }
        public String getDstPath()
        {
            return dstPath;
        }
        public boolean move()
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            {
                return mvFileSaf(this);
            }
            else
            {
                return false;
            }
        }
    }


    /**************************************************************************
     *
     * constructor
     *
     *************************************************************************/
    OpsSafMode(Context context, Uri treeUri, Uri destUri, boolean sortYear, boolean sortMonth, boolean sortDay)
    {
        super(context, sortYear, sortMonth, sortDay);
        if (pathsOverlap(treeUri, destUri))
        {
            mRootDir = null;
            mDestDir = null;
            return;
        }
        if (destUri == null)
        {
            mDestDir = null;
        }
        else
        {
            mDestDir = DocumentFile.fromTreeUri(mContext, destUri);
            if (mDestDir == null)
            {
                Log.e(LOG_TAG, "OpsFileMode() -- invalid destUri: " + destUri);
                mRootDir = null;
                return;
            }
        }
        mRootDir = DocumentFile.fromTreeUri(mContext, treeUri);
    }


    /**************************************************************************
     *
     * Phase 1: gather move operations
     *
     *************************************************************************/
    public void gatherFiles(ProgressCallBack callback)
    {
        super.gatherFiles(callback);
        gatherDirectory(mRootDir, "", callback);
    }


    /**************************************************************************
     *
     * Phase 2: execute a single move operation
     *
     *************************************************************************/
    @RequiresApi(api = Build.VERSION_CODES.N)
    private boolean mvFileSaf(mvOpSaf op)
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
                        mvOpSaf op = new mvOpSaf();
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
}
