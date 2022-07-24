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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

// the actual work is done here
public class OpsSafMode extends Utils
{
    private static final String LOG_TAG = "CDF : OpsSaf";
    private final DocumentFile mRootDir;      // photo directory in proprietary SAF mode
    private final DocumentFile mDestDir;      // maybe null
    private final ContentResolver mResolver;
    private boolean mbMoveDocumentSupported = true;     // starting optimistic
    private boolean mbCopyDocumentSupported = true;

    // a single move operation in SAF mode
    public class mvOpSaf implements mvOp
    {
        public DocumentFile srcFile;
        public DocumentFile srcDirectory;
        public String dstPath;              // relative to photo directory or destination directory
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
    OpsSafMode(Context context, Uri treeUri, Uri destUri, boolean backupCopy, boolean dryRun, boolean sortYear, boolean sortMonth, boolean sortDay)
    {
        super(context, backupCopy, dryRun, sortYear, sortMonth, sortDay);
        mResolver = mContext.getContentResolver();
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
        if (mRootDir == null)
        {
            Log.e(LOG_TAG, "gatherFiles() -- no directory");
            return;
        }
        if (mDestDir != null)
        {
            gatherDirectory(mDestDir, "", true, callback);
        }
        gatherDirectory(mRootDir, "", false, callback);
    }


    /**************************************************************************
     *
     * Phase 2: execute a single move operation
     *
     *************************************************************************/
    @RequiresApi(api = Build.VERSION_CODES.N)
    private boolean mvFileSaf(mvOpSaf op)
    {
        DocumentFile dstDirectory = (mDestDir != null) ? mDestDir : mRootDir;
        String[] pathFrags = op.dstPath.split("/");
        boolean newDirectory = false;

        //DocumentFile[] temp = dstDirectory.listFiles();     // DEBUG

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

        if ((mbMoveDocumentSupported) && ((mDestDir == null) || !mbBackupCopy))
        {
            try
            {
                Uri newUri = DocumentsContract.moveDocument(mResolver, op.srcFile.getUri(), op.srcDirectory.getUri(), dstDirectory.getUri());
                return newUri != null;
            } catch (Exception e)
            {
                mbMoveDocumentSupported = false;
                Log.e(LOG_TAG, "cannot move file to " + ((newDirectory) ? "new" : "existing") + " directory");
                Log.e(LOG_TAG, "mvFile() -- exception " + e);
            }
        }

        if ((mDestDir != null) && mbCopyDocumentSupported)
        {
            try
            {
                Uri newUri = DocumentsContract.copyDocument(mResolver, op.srcFile.getUri(), dstDirectory.getUri());
                if ((newUri != null) && !mbBackupCopy)
                {
                    op.srcFile.delete();
                    return true;        // copy source to destination, then delete source
                }
                return false;
            }
            catch (Exception ec)
            {
                mbCopyDocumentSupported = false;
                Log.e(LOG_TAG, "cannot copy file to " + ((newDirectory) ? "new" : "existing") + " directory");
                Log.e(LOG_TAG, "mvFile() -- exception " + ec);
            }
        }

        return copyFile(op.srcFile, dstDirectory, !mbBackupCopy);
    }


    /**************************************************************************
     *
     * recursively walk through tree and gather mv operations to mOps
     *
     *************************************************************************/
    private void gatherDirectory(DocumentFile dd, String path, boolean bProcessingDestination, ProgressCallBack callback)
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
                    gatherDirectory(df, path + "/" + name, bProcessingDestination, callback);
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
                        // files in source that are already present in destination must not be processed
                        boolean bProcess = mustBeProcessed(name, path, bProcessingDestination);
                        if (bProcess)
                        {
                            mvOpSaf op = new mvOpSaf();
                            op.srcPath = path + "/";
                            op.dstPath = getDestPath(date);
                            if (op.srcPath.equals(op.dstPath))
                            {
                                Log.d(LOG_TAG, "   already sorted to its date directory");
                                mUnchangedFiles++;
                            } else
                            {
                                op.srcDirectory = dd;
                                op.srcFile = df;
                                mOps.add(op);
                            }
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
     * recursively walk through tree and remove unused date  directories
     *
     * return number of remaining directory entries in
     *
     *************************************************************************/
    private int tidyDirectory(DocumentFile dd, String path, ProgressCallBack callback)
    {
        Log.d(LOG_TAG, "tidyDirectory() -- ENTER DIRECTORY " + dd.getName());
        int numOfRemainingFiles = 0;

        if (mustStop)
        {
            Log.d(LOG_TAG, "tidyDirectory() -- stopped");
            return 1;
        }

        DocumentFile[] entries = dd.listFiles();
        Log.d(LOG_TAG, "tidyDirectory() -- number of files found: " + entries.length);
        for (DocumentFile df: entries)
        {
            if (mustStop)
            {
                numOfRemainingFiles = 1;
                break;
            }

            final String name = df.getName();
            if ((name != null) && df.isDirectory() && isDateDirectory(name))
            {
                if (directoryLevel < maxDirectoryLevel)
                {
                    directoryLevel++;
                    int remain = tidyDirectory(df, path + "/" + name, callback);
                    directoryLevel--;
                    if (remain > 0)
                    {
                        numOfRemainingFiles++;
                    }
                    else
                    {
                        callback.tellProgress("removing empty " + path + "/" + name);
                        if (!mbDryRun)
                        {
                            df.delete();
                        }
                    }
                }
                else
                {
                    numOfRemainingFiles++;
                    Log.w(LOG_TAG, "tidyDirectory() -- path depth overflow, ignoring " + name);
                }

            }
            else
            {
                numOfRemainingFiles++;
            }
        }

        Log.d(LOG_TAG, "tidyDirectory() -- LEAVE DIRECTORY " + dd.getName() + " with " + numOfRemainingFiles + " entries");
        return numOfRemainingFiles;
    }


    /**************************************************************************
     *
     * Phase 3: remove empty directories that look like date/time related
     *
     *************************************************************************/
    public void removeUnusedDateFolders(ProgressCallBack callback)
    {
        super.removeUnusedDateFolders(callback);
        DocumentFile destDir = (mDestDir != null) ? mDestDir : mRootDir;
        tidyDirectory(destDir, "", callback);
    }


    /**************************************************************************
     *
     * Copy file to destination directory
     *
     *************************************************************************/
    private boolean copyFile
    (
        DocumentFile sourceDocument,
        DocumentFile targetParentDocument,
        boolean bRemoveSrcOnSuccess
    )
    {
        //
        // open source file for reading
        //

        final String name = sourceDocument.getName();
        if (name == null)
        {
            Log.e(LOG_TAG, "cannot get name: " + sourceDocument.getUri());
            return false;
        }

        InputStream is;
        try
        {
            is = mResolver.openInputStream(sourceDocument.getUri());
        } catch (Exception e)
        {
            // cannot open source for reading: fatal failure
            Log.e(LOG_TAG, "I/O exception: " + e);
            return false;
        }

        //
        // create a new destination file
        //

        String type = mResolver.getType(sourceDocument.getUri());
        DocumentFile df = targetParentDocument.createFile(type, name);
        if (df == null)
        {
            // cannot create destination file: fatal failure
            Log.e(LOG_TAG, "cannot create destination file " + name + " in: " + targetParentDocument.getUri());
            closeStream(is);
            return false;
        }

        //
        // copy data
        //

        OutputStream os;
        try
        {
            os = mResolver.openOutputStream(df.getUri());
            if (copyStream(is, os))
            {
                if (bRemoveSrcOnSuccess)
                {
                    sourceDocument.delete();
                }
                return true;
            }
        } catch (FileNotFoundException e)
        {
            closeStream(is);
            Log.e(LOG_TAG, "cannot create output stream");
        }

        return false;
    }
}
