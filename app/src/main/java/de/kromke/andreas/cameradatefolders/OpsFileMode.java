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

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import java.io.File;

// the actual work is done here
public class OpsFileMode extends Utils
{
    private static final String LOG_TAG = "CDF : OpsFile";
    private final File mRootDirFile;          // photo directory in traditional File mode
    private final File mDestDirFile;          // maybe null

    // a single move operation in File mode
    public class mvOpFile implements mvOp
    {
        public String srcPath;              // debug helper
        public File srcFile;
        public File srcDirectory;
        public String dstPath;              // relative to photo directory

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
            return mvFile(this);
        }
    }


    /**************************************************************************
     *
     * constructor
     *
     *************************************************************************/
    OpsFileMode(Context context, Uri treeUri, Uri destUri, boolean backupCopy, boolean sortYear, boolean sortMonth, boolean sortDay)
    {
        super(context, backupCopy, sortYear, sortMonth, sortDay);

        if (pathsOverlap(treeUri, destUri))
        {
            mRootDirFile = null;
            mDestDirFile = null;
            return;
        }

        if (destUri == null)
        {
            mDestDirFile = null;
        }
        else
        {
            String p = UriToPath.getPathFromUri(context, destUri);
            if (p == null)
            {
                Log.e(LOG_TAG, "OpsFileMode() -- invalid destUri: " + destUri);
                mRootDirFile = null;
                mDestDirFile = null;
                return;
            }
            mDestDirFile = new File(p);
        }

        String p = UriToPath.getPathFromUri(context, treeUri);
        if (p != null)
        {
            mRootDirFile = new File(p);
        }
        else
        {
            mRootDirFile = null;
        }
    }


    /**************************************************************************
     *
     * Phase 1: gather move operations
     *
     *************************************************************************/
    public void gatherFiles(ProgressCallBack callback)
    {
        if (mRootDirFile == null)
        {
            Log.e(LOG_TAG, "gatherFiles() -- no directory");
            return;
        }
        super.gatherFiles(callback);
        gatherDirectoryFileMode(mRootDirFile, "", callback);
    }


    /**************************************************************************
     *
     * Phase 2: execute a single move operation
     *
     *************************************************************************/
    private boolean mvFile(mvOpFile op)
    {
        File dstDirectory = (mDestDirFile != null) ? mDestDirFile : mRootDirFile;
        String[] pathFrags = op.dstPath.split("/");
        boolean newDirectory = false;

        for (String frag: pathFrags)
        {
            if (!frag.isEmpty())
            {
                // findFile() is awfully slow. Use LRU cache.
                //DocumentFile nextDirectory = mFfCache.findFileCached(dstDirectory, frag);
                File nextDirectory = new File(dstDirectory, frag);
                if (nextDirectory.exists())
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
                    if (nextDirectory.mkdir())
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

        File dstFile = new File(dstDirectory, op.srcFile.getName());
        try
        {
            return op.srcFile.renameTo(dstFile);
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
     * (similar to gatherDirectory(), but in File instead of SAF mode)
     *
     *************************************************************************/
    private void gatherDirectoryFileMode(File dd, String path, ProgressCallBack callback)
    {
        Log.d(LOG_TAG, "gatherDirectoryFileMode() -- ENTER DIRECTORY " + dd.getName());

        if (mustStop)
        {
            Log.d(LOG_TAG, "gatherDirectoryFileMode() -- stopped");
            return;
        }

        File[] entries = dd.listFiles();
        if (entries == null)
        {
            entries = new File[0];  // replace null ptr with empty array
        }
        Log.d(LOG_TAG, "gatherDirectoryFileMode() -- number of files found: " + entries.length);
        for (File df: entries)
        {
            if (mustStop)
            {
                return;
            }

            final String name = df.getName();
            if (name.startsWith("."))
            {
                Log.w(LOG_TAG, "gatherDirectoryFileMode() -- skip dot files: " + name);
            }
            else
            if (df.isDirectory())
            {
                if (directoryLevel < maxDirectoryLevel)
                {
                    directoryLevel++;
                    gatherDirectoryFileMode(df, path + "/" + name, callback);
                    directoryLevel--;
                }
                else
                {
                    Log.w(LOG_TAG, "gatherDirectoryFileMode() -- path depth overflow, ignoring " + name);
                }

            }
            else
            {
                mFiles++;
                if (mFiles > maxFiles)
                {
                    Log.w(LOG_TAG, "gatherDirectoryFileMode() -- DEBUG LIMIT: max number " + maxFiles + " of files exceeded");
                    return;
                }

                if (isCameraFileType(name))
                {
                    camFileDate date = isCameraFile(name);
                    if (date != null)
                    {
                        Log.d(LOG_TAG, "gatherDirectoryFileMode() -- camera file found: " + path + "/" + name);
                        mvOpFile op = new mvOpFile();
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
                        Log.w(LOG_TAG, "gatherDirectoryFileMode() -- image file does not look like camera file: " + name);
                    }
                    callback.tellProgress("" + mOps.size() + "/" + mUnchangedFiles);
                }
                else
                {
                    Log.w(LOG_TAG, "gatherDirectoryFileMode() -- non matching file type: " + name);
                }
            }
        }

        Log.d(LOG_TAG, "gatherDirectoryFileMode() -- LEAVE DIRECTORY " + dd.getName());
    }

}
