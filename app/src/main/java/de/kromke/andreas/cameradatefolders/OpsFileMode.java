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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;


/** @noinspection JavadocBlankLines, RedundantSuppression */ // the actual work is done here
public class OpsFileMode extends Utils
{
    private static final String LOG_TAG = "CDF : OpsFile";
    private final File mRootDir;          // photo directory
    private final File mDestDir;          // maybe null
    private boolean mbMoveDocumentSupported = true;     // starting optimistic

    // a single move operation in File mode
    public class mvOpFile implements mvOp
    {
        public String srcPath;              // debug helper
        public File srcFile;
        public File srcDirectory;
        public String dstPath;              // relative to photo directory
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
            return mvFile(this);
        }
    }


    /**************************************************************************
     *
     * constructor
     *
     *************************************************************************/
    OpsFileMode
    (
        Context context,
        Uri treeUri, Uri destUri,
        boolean backupCopy, boolean dryRun,
        boolean sortYear, boolean sortMonth, boolean sortDay
    )
    {
        super(context, backupCopy, dryRun, sortYear, sortMonth, sortDay);

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
            String p = UriToPath.getPathFromUri(context, destUri);
            if (p == null)
            {
                Log.e(LOG_TAG, "OpsFileMode() -- invalid destUri: " + destUri);
                mRootDir = null;
                mDestDir = null;
                mErrCode = -10;
                return;
            }
            mDestDir = new File(p);
        }

        String p = UriToPath.getPathFromUri(context, treeUri);
        if (p == null)
        {
            mRootDir = null;
            mErrCode = -11;
        }
        else
        {
            mRootDir = new File(p);
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
            callback.tellProgress("ERROR: invalid path(s)");
            Log.e(LOG_TAG, "gatherFiles() -- no directory");
            return -1;
        }
        if (mDestDir != null)
        {
            if (!mDestDir.canWrite())
            {
                callback.tellProgress("ERROR: Destination directory is not writeable in File mode.\n");
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
            callback.tellProgress("ERROR: invalid path(s)");
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
    private boolean mvFile(mvOpFile op)
    {
        File dstDirectory = (mDestDir != null) ? mDestDir : mRootDir;
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
                        Log.d(LOG_TAG, "mvFile() -- created directory: " + frag);
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
            File dstFile = new File(dstDirectory, op.srcFile.getName());
            try
            {
                if (op.srcFile.renameTo(dstFile))
                {
                    return true;
                }
                Log.e(LOG_TAG, "cannot move file to " + ((newDirectory) ? "new" : "existing") + " directory");
                mMoveFileFailures++;
                return false;
            } catch (Exception e)
            {
                mbMoveDocumentSupported = false;
                Log.e(LOG_TAG, "cannot move file to " + ((newDirectory) ? "new" : "existing") + "directory");
                Log.e(LOG_TAG, "mvFile() -- exception " + e);
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
     * (similar to gatherDirectory(), but in File instead of SAF mode)
     *
     * return number of entries in that directory
     *
     *************************************************************************/
    private int gatherDirectory(File dd, String path,  boolean bProcessingDestination, ProgressCallBack callback)
    {
        Log.d(LOG_TAG, "gatherDirectoryFileMode() -- ENTER DIRECTORY " + dd.getName());
        int nEntries; // dummy

        if (mustStop)
        {
            Log.d(LOG_TAG, "gatherDirectoryFileMode() -- stopped");
            return 1;
        }
        boolean bComparePaths = bProcessingDestination || (mDestDir == null);

        // note that listFiles() does not find "." and ".."
        File[] entries = dd.listFiles();
        if (entries == null)
        {
            entries = new File[0];  // replace null ptr with empty array
        }
        nEntries = entries.length;
        Log.d(LOG_TAG, "gatherDirectoryFileMode() -- number of files found: " + entries.length);

        for (File df: entries)
        {
            if (mustStop)
            {
                return 1;   // dummy
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
                    Log.w(LOG_TAG, "gatherDirectoryFileMode() -- path depth overflow, ignoring " + name);
                }
            }
            else
            {
                mFiles++;
                if (mFiles > maxFiles)
                {
                    Log.w(LOG_TAG, "gatherDirectoryFileMode() -- DEBUG LIMIT: max number " + maxFiles + " of files exceeded");
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
                            mvOpFile op = new mvOpFile();
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
                        Log.w(LOG_TAG, "gatherDirectoryFileMode() -- image file does not look like camera file: " + name);
                        mIgnoredImageFiles++;
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
        return nEntries;
    }


    /**************************************************************************
     *
     * recursively walk through tree and remove unused date directories
     *
     * return number of remaining directory entries
     *
     *************************************************************************/
    private int tidyDirectory(File dd, String path, ProgressCallBack callback)
    {
        Log.d(LOG_TAG, "tidyDirectory() -- ENTER DIRECTORY " + dd.getName());
        int numOfRemainingFiles = 0;

        if (mustStop)
        {
            Log.d(LOG_TAG, "tidyDirectory() -- stopped");
            return 1;
        }

        // note that listFiles() does not find "." and ".."
        File[] entries = dd.listFiles();
        if (entries == null)
        {
            entries = new File[0];  // replace null ptr with empty array
        }
        Log.d(LOG_TAG, "tidyDirectory() -- number of files found: " + entries.length);
        for (File df: entries)
        {
            if (mustStop)
            {
                numOfRemainingFiles = 1;
                break;
            }

            final String name = df.getName();
            if (df.isDirectory() && isDateDirectory(name))
            {
                if (directoryLevel < maxDirectoryLevel)
                {
                    directoryLevel++;
                    // recursion, i.e. check if directory is empty
                    int remain = tidyDirectory(df, path + "/" + name, callback);
                    directoryLevel--;
                    if (remain > 0)
                    {
                        // directory is not empty
                        numOfRemainingFiles++;
                    }
                    else
                    {
                        // directory is empty
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
        File destDir = (mDestDir != null) ? mDestDir : mRootDir;
        tidyDirectory(destDir, "", callback);
    }


    /**************************************************************************
     *
     * Copy file to destination directory
     *
     *************************************************************************/
    private boolean copyFile
    (
        File sourceDocument,
        File targetParentDocument,
        boolean bRemoveSrcOnSuccess
    )
    {
        //
        // open source file for reading
        //

        final String name = sourceDocument.getName();

        InputStream is;
        //noinspection RedundantSuppression
        try
        {
            //noinspection IOStreamConstructor (suggested replacement needs API 26, we have 19)
            is = new FileInputStream(sourceDocument);
        } catch (Exception e)
        {
            // cannot open source for reading: fatal failure
            Log.e(LOG_TAG, "I/O exception: " + e);
            return false;
        }

        //
        // create a new destination file
        //

        File df = new File(targetParentDocument, name);
        try
        {
            if (!df.createNewFile())
            {
                // cannot open source for reading: fatal failure
                Log.e(LOG_TAG, "cannot create new file: " + df);
                return false;
            }
        } catch (Exception e)
        {
            // cannot open source for reading: fatal failure
            Log.e(LOG_TAG, "I/O exception: " + e);
            return false;
        }

        //
        // copy data
        //

        try
        {
            FileOutputStream os = new FileOutputStream(df);
            if (copyStream(is, os))
            {
                if (bRemoveSrcOnSuccess)
                {
                    if (!sourceDocument.delete())
                    {
                        Log.e(LOG_TAG, "cannot delete source file " + sourceDocument);
                    }
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
