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

import static java.lang.System.currentTimeMillis;

class WorkerThread implements Runnable, Utils.ProgressCallBack
{
    private static final String LOG_TAG = "CDF : WT";
    public boolean isBusy = false;
    private boolean mMustStop = false;
    private final MyApplication mApplication;
    // parameters
    private Context mContext = null;
    private Uri mTreeUri = null;
    private Uri mDestTreeUri = null;
    boolean mbSortYear = true;
    boolean mbSortMonth = true;
    boolean mbSortDay = true;
    private boolean mbBackupCopy = false;
    private boolean mbDryRun = false;
    private boolean mbFileMode = false;
    private Utils mUtils = null;
    // result
    private static final int nMaxFailures = 10;
    private int nSuccess = 0;
    private int nFailure = 0;
    private int nUnchanged = 0;

    public WorkerThread(MyApplication application)
    {
        mApplication = application;
    }

    public void setParameters
    (
        Context context,
        Uri srcUri, Uri dstUri,
        String scheme,
        boolean backupCopy, boolean bDryRun, boolean bFileMode
    )
    {
        mTreeUri = srcUri;
        switch (scheme)
        {
            default:
            case "ymd":
                mbSortYear = true;
                mbSortMonth = true;
                mbSortDay = true;
                break;
            case "md":
                mbSortYear = false;
                mbSortMonth = true;
                mbSortDay = true;
                break;
            case "yd":
                mbSortYear = true;
                mbSortMonth = false;
                mbSortDay = true;
                break;
            case "ym":
                mbSortYear = true;
                mbSortMonth = true;
                mbSortDay = false;
                break;
            case "d":
                mbSortYear = false;
                mbSortMonth = false;
                mbSortDay = true;
                break;
            case "m":
                mbSortYear = false;
                mbSortMonth = true;
                mbSortDay = false;
                break;
            case "y":
                mbSortYear = true;
                mbSortMonth = false;
                mbSortDay = false;
                break;
            case "flat":
                mbSortYear = false;
                mbSortMonth = false;
                mbSortDay = false;
                break;
        }
        mDestTreeUri = dstUri;
        mbBackupCopy = backupCopy;
        mbDryRun = bDryRun;
        mbFileMode = bFileMode;
        mContext = context;
    }

    private void done(int err)
    {
        if (mApplication != null)
        {
            String text = null;
            if ((err == -10) || (err == -11))
            {
                text = "No valid path(s). Location(s) can only be accessed in SAF mode.";
                nSuccess = err;
            }
            else
            if (err == -4)
            {
                text = "Source and destination paths overlap.";
                nSuccess = err;
            }
            mApplication.msgFromWorkerThread(nSuccess, nFailure, nUnchanged, text, true);
        }
        isBusy = false;
    }

    // also used as callback from Utils
    public void tellProgress(final String text)
    {
        if (mApplication != null)
        {
            mApplication.msgFromWorkerThread(nSuccess, nFailure, nUnchanged, text, false);
        }
    }

    // main thread function, runs in thread context
    public void run()
    {
        isBusy = true;
        mMustStop = false;
        Log.d(LOG_TAG, "run()");

        nSuccess = 0;
        nFailure = 0;
        nUnchanged = 0;
        int err = 0;
        if (mTreeUri != null)
        {
            final long startTime = currentTimeMillis();

            if (mbFileMode)
            {
                mUtils = new OpsFileMode(mContext, mTreeUri, mDestTreeUri, mbBackupCopy, mbDryRun, mbSortYear, mbSortMonth, mbSortDay);
            }
            else
            {
                mUtils = new OpsSafMode(mContext, mTreeUri, mDestTreeUri, mbBackupCopy, mbDryRun, mbSortYear, mbSortMonth, mbSortDay);
            }

            if (mUtils.mErrCode < 0)
            {
                tellProgress("Invalid directory/ies. Please reselect!");
                err = mUtils.mErrCode;
            }
            else
            {
                tellProgress("Collecting files ...");
                err = mUtils.gatherFilesDst(this);
            }
            if (err == 0)
            {
                err = mUtils.gatherFilesSrc(this);
            }
            if (err == 0)
            {
                int ret = mUtils.getOps().size();
                if (mMustStop && (ret == 0))
                {
                    tellProgress("stopped on demand");
                }

                /*
                /// +test code
                for (int i = 0; i < 100; i++)
                {
                    tellProgress("" + i + " lines");
                }
                /// -test code
                */
                nUnchanged = mUtils.mUnchangedFiles;
                if (ret > 0)
                {
                    tellProgress("" + ret + " files collected.\n\nStart file operations ...");
                    int i = 0;
                    for (Utils.mvOp op : mUtils.getOps())
                    {
                        if (mMustStop)
                        {
                            tellProgress("stopped on demand.");
                            break;
                        }

                        String fileName = op.getName();
                        Log.d(LOG_TAG, " mv " + op.getSrcPath() + fileName + " ==> " + op.getDstPath());
                        if (mbDryRun)
                        {
                            nSuccess++;
                        } else
                        {
                            boolean retm = op.move();
                            if (retm)
                            {
                                nSuccess++;
                            } else
                            {
                                nFailure++;
                            }
                        }
                        i++;
                        tellProgress(fileName + " (" + i + "/" + mUtils.mOps.size() + ")");
                        if (nFailure > nMaxFailures)
                        {
                            break;
                        }
                    }

                    Log.d(LOG_TAG, "files moved: " + nSuccess + ", failures: " + nFailure);
                    Log.d(LOG_TAG, "folders created: " + mUtils.mMkdirSuccesses + ", failures: " + mUtils.mMkdirFailures);

                    if (mUtils.mMkdirSuccesses > 0)
                    {
                        tellProgress("-> folders created: " + mUtils.mMkdirSuccesses + "\n");
                    }
                    if (mUtils.mMkdirFailures > 0)
                    {
                        tellProgress("-> folder create failures: " + mUtils.mMkdirFailures + "\n");
                    }

                    if (nFailure > nMaxFailures)
                    {
                        tellProgress("* STOP *: maximum number of failures exceeded.\n");
                    }
                    else
                    {
                        tellProgress(" ... file operations done.\n");
                    }
                }

                if (!mMustStop)
                {
                    if (StatusAndPrefs.mbSkipTidy)
                    {
                        tellProgress("Skipping tidy up ...");
                    }
                    else
                    {
                        tellProgress("Tidy up ...");
                        mUtils.removeUnusedDateFolders(this);
                        Log.d(LOG_TAG, "folders removed: " + mUtils.mRmdirSuccesses + ", failures: " + mUtils.mRmdirFailures);
                        if (mUtils.mRmdirSuccesses > 0)
                        {
                            tellProgress("-> folders removed: " + mUtils.mRmdirSuccesses + "\n");
                        }
                        if (mUtils.mRmdirFailures > 0)
                        {
                            tellProgress("-> folder remove failures: " + mUtils.mRmdirFailures + "\n");
                        }

                        if (!mMustStop)
                        {
                            tellProgress("... done\n");
                        } else
                        {
                            tellProgress("stopped on demand.");
                        }
                    }
                }
            }

            if ((mUtils.mMkdirFailures == 0) && (mUtils.mMkdirSuccesses > 0) &&(mUtils.mMoveFileFailures > 0))
            {
                tellProgress("ANDROID BUG: Could create directories, but could not move files. Either grant full file access or use slooooow SAF.\n");
            }
            final String timeSpent = Utils.getTimeStr(currentTimeMillis() - startTime);
            tellProgress(timeSpent + " spent.");
        }

        mUtils = null;
        done(err);
    }

    public void stop()
    {
        if (isBusy)
        {
            mMustStop = true;
            if (mUtils != null)
            {
                mUtils.mustStop = true;
            }
        }
    }
}
