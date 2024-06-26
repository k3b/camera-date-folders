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

/** @noinspection JavadocBlankLines*/ // the actual work is done here
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
        public boolean bCopy;               // copy from source to destination

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
            mErrCode = -4;
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
                Log.e(LOG_TAG, "OpsSafMode() -- invalid destUri: " + destUri);
                mRootDir = null;
                mErrCode = -3;
                return;
            }
        }

        DocumentFile rootDir = DocumentFile.fromTreeUri(mContext, treeUri);
        if (rootDir != null)
        {
            // debug stuff
            /*
            Log.d(LOG_TAG, "OpsSafMode() -- treeUri: " + rootDir.getUri());
            Log.d(LOG_TAG, "             --  scheme: " + rootDir.getUri().getScheme());
            Log.d(LOG_TAG, "             --  authority: " + rootDir.getUri().getAuthority());
            Log.d(LOG_TAG, "             --  path: " + rootDir.getUri().getPath());
            Log.d(LOG_TAG, "             --  path segments: " + rootDir.getUri().getPathSegments());
            Log.d(LOG_TAG, "             -- name: " + rootDir.getName());
            Log.d(LOG_TAG, "             -- exists: " + rootDir.exists());
            Log.d(LOG_TAG, "             -- isDirectory: " + rootDir.isDirectory());
            Log.d(LOG_TAG, "             -- canRead: " + rootDir.canRead());
            Log.d(LOG_TAG, "             -- canWrite: " + rootDir.canWrite());
            */
            if (!rootDir.isDirectory() || !rootDir.canRead())
            {
                rootDir = null;
            }
        }

        mRootDir = rootDir;
        if (mRootDir == null)
        {
            Log.e(LOG_TAG, "OpsSafMode() -- invalid srcUri: " + treeUri);
            mErrCode = -1;
        }
    }


    /**************************************************************************
     *
     * Phase 1a: gather move operations for destination path, if any
     *
     *************************************************************************/
    public int gatherFilesDst(ProgressCallBack callback)
    {
        super.gatherFilesDst(callback);
        if (mRootDir == null)
        {
            Log.e(LOG_TAG, "gatherFiles() -- no directory");
            return -1;
        }
        if (mDestDir != null)
        {
            if (!mDestDir.canWrite())
            {
                callback.tellProgress("ERROR: Destination directory is not writeable in SAF mode.\n");
                return -2;
            }
            callback.tellProgress("scanning destination path...");
            gatherDirectory(mDestDir, "", true, callback);
            callback.tellProgress("...done");
        }

        return 0;
    }


    /**************************************************************************
     *
     * Phase 1b: gather move operations for source path
     *
     *************************************************************************/
    public int gatherFilesSrc(ProgressCallBack callback)
    {
        super.gatherFilesSrc(callback);
        if (mRootDir == null)
        {
            Log.e(LOG_TAG, "gatherFiles() -- no directory");
            return -1;
        }
        if (mDestDir != null)
        {
            callback.tellProgress("scanning source path...");
        }
        gatherDirectory(mRootDir, "", false, callback);
        if (mDestDir != null)
        {
            callback.tellProgress("...done");
        }

        return 0;
    }


    /**************************************************************************
     *
     * Phase 2: execute a single move or copy operation
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
                        mMkdirSuccesses++;
                        dstDirectory = nextDirectory;
                        newDirectory = true;
                    }
                    else
                    {
                        Log.e(LOG_TAG, "mvFile() -- cannot create directory: " + frag);
                        mMkdirFailures++;
                        return false;
                    }
                }
            }
        }

        //
        // First try atomic move operation (in destination folder or if no destination was given)
        //

        if ((mbMoveDocumentSupported) && (!op.bCopy))
        {
            try
            {
                Uri newUri = DocumentsContract.moveDocument(mResolver, op.srcFile.getUri(), op.srcDirectory.getUri(), dstDirectory.getUri());
                if (newUri != null)
                {
                    // move was successful
                    return true;
                }
                Log.e(LOG_TAG, "cannot move file to " + ((newDirectory) ? "new" : "existing") + " directory");
                mMoveFileFailures++;
                return false;
            } catch (Exception e)
            {
                mbMoveDocumentSupported = false;
                Log.e(LOG_TAG, "cannot move file to " + ((newDirectory) ? "new" : "existing") + " directory");
                Log.e(LOG_TAG, "mvFile() -- exception " + e);
            }
        }

        //
        // Try atomic copy operation (not inside source folder!)
        // and delete source, if to be moved from source to destination folder or inside destination folder
        //

        if ((mDestDir != null) && mbCopyDocumentSupported)
        {
            try
            {
                Uri newUri = DocumentsContract.copyDocument(mResolver, op.srcFile.getUri(), dstDirectory.getUri());
                if (newUri != null)
                {
                    // copy was successful, now delete source, if requested
                    if (!op.bCopy)
                    {
                        op.srcFile.delete();
                    }
                    return true;
                }
                Log.e(LOG_TAG, "cannot copy file to " + ((newDirectory) ? "new" : "existing") + " directory");
                return false;
            }
            catch (Exception ec)
            {
                mbCopyDocumentSupported = false;
                Log.e(LOG_TAG, "cannot copy file to " + ((newDirectory) ? "new" : "existing") + " directory");
                Log.e(LOG_TAG, "mvFile() -- exception " + ec);
            }
        }

        //
        // Finally do copy-delete operation manually
        //

        if (copyFile(op.srcFile, dstDirectory, !op.bCopy))
        {
            return true;
        }
        else
        {
            mCopyFileFailures++;
            return false;
        }
    }


    /**************************************************************************
     *
     * recursively walk through tree and gather mv operations to mOps
     *
     * return number of entries in that directory
     *
     *************************************************************************/
    private int gatherDirectory(DocumentFile dd, String path, boolean bProcessingDestination, ProgressCallBack callback)
    {
        Log.d(LOG_TAG, "gatherDirectory() -- ENTER DIRECTORY " + dd.getName());
        int nEntries;

        if (mustStop)
        {
            Log.d(LOG_TAG, "gatherDirectory() -- stopped");
            return 1;   // dummy
        }

        boolean bComparePaths = bProcessingDestination || (mDestDir == null);

        // note that listFiles() does not find "." and ".."
        DocumentFile[] entries = dd.listFiles();
        nEntries = entries.length;
        Log.d(LOG_TAG, "gatherDirectory() -- number of files found: " + entries.length);
        for (DocumentFile df: entries)
        {
            if (mustStop)
            {
                return 1;   // dummy
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
                    int nSubEntries = gatherDirectory(df, path + "/" + name, bProcessingDestination, callback);
                    directoryLevel--;
                    if (isDateDirectory(name) && nSubEntries == 0)
                    {
                        Log.w(LOG_TAG, "gatherDirectoryFileMode() -- empty directory found: " + name);
                        mEmptyDateDirs++;
                    }
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
                    return nEntries;
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
                            if (bComparePaths && op.srcPath.equals(op.dstPath))
                            {
                                Log.d(LOG_TAG, "   already sorted to its date directory");
                                mUnchangedFiles++;
                            } else
                            {
                                op.srcDirectory = dd;
                                op.srcFile = df;
                                op.bCopy = (!bProcessingDestination && mbBackupCopy);
                                mOps.add(op);
                            }
                        }
                    }
                    else
                    {
                        Log.w(LOG_TAG, "gatherDirectory() -- image file does not look like camera file: " + name);
                        mIgnoredImageFiles++;
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
        return nEntries;
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
                            if (df.delete())
                            {
                                mRmdirSuccesses++;
                            }
                            else
                            {
                                Log.e(LOG_TAG, "cannot delete empty directory " + df);
                                mRmdirFailures++;
                            }
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
        if (mRootDir == null)
        {
            Log.e(LOG_TAG, "removeUnusedDateFolders() -- no directory");
            return;
        }
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
        if (is == null)
        {
            // cannot open source for reading: fatal failure
            Log.e(LOG_TAG, "cannot open: " + sourceDocument.getUri());
            return false;
        }

        //
        // create a new destination file
        //

        String type = mResolver.getType(sourceDocument.getUri());
        if (type == null)
        {
            Log.w(LOG_TAG, "no mime type for source file " + name + " in: " + sourceDocument.getUri());
            type = "application/octet-stream";      // used generic type instead
        }
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
