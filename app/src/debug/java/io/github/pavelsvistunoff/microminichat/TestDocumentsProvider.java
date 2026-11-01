package io.github.pavelsvistunoff.microminichat;

import android.database.*;
import android.os.*;
import android.provider.*;
import java.io.*;

/** Test-APK-only provider. No provider is present in the released app. */
public final class TestDocumentsProvider extends DocumentsProvider {
    public static final String AUTHORITY="io.github.pavelsvistunoff.microminichat.test.documents";
    @Override public boolean onCreate(){return true;}
    private File file(String id)throws FileNotFoundException {
        if(!id.matches("[a-zA-Z0-9._-]{1,80}"))throw new FileNotFoundException();
        return new File(getContext().getFilesDir(),id);
    }
    @Override public Cursor queryRoots(String[] projection){return new MatrixCursor(projection==null?new String[]{DocumentsContract.Root.COLUMN_ROOT_ID}:projection);}
    @Override public Cursor queryChildDocuments(String parent,String[] projection,String order){return new MatrixCursor(projection==null?new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID}:projection);}
    @Override public Cursor queryDocument(String id,String[] projection)throws FileNotFoundException {
        File file=file(id);String[] columns=projection==null?new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_FLAGS}:projection;
        MatrixCursor cursor=new MatrixCursor(columns);Object[] values=new Object[columns.length];
        for(int i=0;i<columns.length;i++){
            switch(columns[i]){
                case DocumentsContract.Document.COLUMN_DOCUMENT_ID:values[i]=id;break;
                case DocumentsContract.Document.COLUMN_DISPLAY_NAME:values[i]=id;break;
                case DocumentsContract.Document.COLUMN_SIZE:values[i]=file.exists()?file.length():0L;break;
                case DocumentsContract.Document.COLUMN_MIME_TYPE:values[i]="application/octet-stream";break;
                case DocumentsContract.Document.COLUMN_FLAGS:values[i]=DocumentsContract.Document.FLAG_SUPPORTS_WRITE|DocumentsContract.Document.FLAG_SUPPORTS_DELETE;break;
                default:values[i]=null;
            }
        }cursor.addRow(values);return cursor;
    }
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(id),ParcelFileDescriptor.parseMode(mode));
    }
    @Override public String createDocument(String parent,String mime,String name)throws FileNotFoundException {
        try{if(!file(name).createNewFile())throw new IOException();return name;}catch(IOException e){throw new FileNotFoundException();}
    }
    @Override public void deleteDocument(String id)throws FileNotFoundException{if(!file(id).delete())throw new FileNotFoundException();}
}
