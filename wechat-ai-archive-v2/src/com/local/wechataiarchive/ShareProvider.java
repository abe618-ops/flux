package com.local.wechataiarchive;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/** Read-only provider. Only explicit, temporary URI grants expose a generated file. */
public final class ShareProvider extends ContentProvider {
    public static final String AUTHORITY="com.local.wechataiarchive.files";
    @Override public boolean onCreate(){return true;}
    private File resolve(Uri uri) throws FileNotFoundException {
        List<String> p=uri.getPathSegments();
        if(!AUTHORITY.equals(uri.getAuthority())||p.size()!=3||!"export".equals(p.get(0))||!p.get(1).matches("[A-Za-z0-9_-]+")||!p.get(2).matches("[A-Za-z0-9_.-]+"))throw new FileNotFoundException("Invalid export URI");
        try {File root=new File(getContext().getFilesDir(),"exports").getCanonicalFile();File f=new File(new File(root,p.get(1)),p.get(2)).getCanonicalFile();if(!f.getPath().startsWith(root.getPath()+File.separator)||!f.isFile())throw new FileNotFoundException("Export not found");return f;}catch(java.io.IOException e){throw new FileNotFoundException(e.getMessage());}
    }
    public static Uri uri(String id,String name){return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("export").appendPath(id).appendPath(name).build();}
    @Override public String getType(Uri u){String n=u.getLastPathSegment();if(n==null)return "application/octet-stream";if(n.endsWith(".pdf"))return "application/pdf";if(n.endsWith(".md"))return "text/markdown";if(n.endsWith(".txt"))return "text/plain";if(n.endsWith(".html"))return "text/html";if(n.endsWith(".zip"))return "application/zip";if(n.endsWith(".epub"))return "application/epub+zip";if(n.endsWith(".png"))return "image/png";if(n.endsWith(".jpg"))return "image/jpeg";return "application/octet-stream";}
    @Override public Cursor query(Uri u,String[] projection,String selection,String[] selectionArgs,String sort){try{File f=resolve(u);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(columns,1);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?f.getName():OpenableColumns.SIZE.equals(columns[i])?f.length():null;c.addRow(row);return c;}catch(FileNotFoundException e){return null;}}
    @Override public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(resolve(u),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException("Read only");}
}
