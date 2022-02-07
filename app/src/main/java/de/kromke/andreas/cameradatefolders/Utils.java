package de.kromke.andreas.cameradatefolders;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;

import java.util.ArrayList;

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

public class Utils
{
    private static final String LOG_TAG = "CDF : Utils";
    Context mContext;
    DocumentFile mRootDir;
    int directoryLevel;
    boolean m_SortYear;
    boolean m_SortMonth;
    boolean m_SortDay;
    public ArrayList<mvOp> mOps = null;

    public static class camFileDate
    {
        public String year;        // 1999 .. 2999
        public String month;       // 1 .. 12
        public String day;         // 1 .. 31
    }

    public static class mvOp
    {
        public String srcPath;              // debug helper
        public DocumentFile srcFile;
        public DocumentFile srcDirectory;
        public String dstPath;
    }

    Utils(Context context, Uri treeUri, boolean sortYear, boolean sortMonth, boolean sortDay)
    {
        mContext = context;
        mRootDir = DocumentFile.fromTreeUri(mContext, treeUri);
        m_SortYear = sortYear;
        m_SortMonth = sortMonth;
        m_SortDay = sortDay;
    }

    public int gatherFiles()
    {
        mOps = new ArrayList<>();
        directoryLevel = 8;             // limit
        if (mRootDir != null)
        {
            gatherDirectory(mRootDir, "");
        }
        return mOps.size();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public boolean mvFile(mvOp op)
    {
        ContentResolver content = mContext.getContentResolver();
        DocumentFile dstDirectory = mRootDir;
        String[] pathFrags = op.dstPath.split("/");
        for (String frag: pathFrags)
        {
            if (!frag.isEmpty())
            {
                DocumentFile nextDirectory = dstDirectory.findFile(frag);
                if (nextDirectory != null)
                {
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
                    nextDirectory = dstDirectory.createDirectory(frag);
                    if (nextDirectory != null)
                    {
                        dstDirectory = nextDirectory;
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
            Log.e(LOG_TAG, "mvFile() -- exception " + e);
        }
        return false;
    }

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
        // get year
        //

        ret.day = name.substring(i + 6, i + 8);
        int day = getNumber(ret.day);
        if ((day < 1) || (day > 31))
        {
            return null;
        }

        return ret;
    }

    private String getDestPath(camFileDate date)
    {
        String ret = "";
        if (m_SortYear)
        {
            ret = date.year + "/";
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

    private void gatherDirectory(DocumentFile dd, String path)
    {
        directoryLevel--;
        if (directoryLevel < 0)
        {
            Log.w(LOG_TAG, "gatherDirectory() -- path depth overflow");
            return;
        }

        Log.d(LOG_TAG, "gatherDirectory() -- ENTER DIRECTORY " + dd.getName());

        DocumentFile[] entries = dd.listFiles();
        Log.d(LOG_TAG, "gatherDirectory() -- number of files found: " + entries.length);
        for (DocumentFile df: entries)
        {
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
                gatherDirectory(df, path + "/" + name);
            }
            else
            {
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
