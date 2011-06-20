package com.matthieu;


import android.content.Context;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class RefHanziDBImporter {
    private Context my_context;
    
    public void RefHanziDBImporter(Context context, InputStream input, String destination) {
        my_context = context;
        try {
            copyDataBase(input, destination);
        } catch (IOException e) {

        }
    }


    private void copyDataBase(InputStream input, String destination) throws IOException {
    	OutputStream output = new FileOutputStream(destination);

    	//transfer bytes
    	byte[] buffer = new byte[1024];
    	int length;
    	while ((length = input.read(buffer))>0){
    		output.write(buffer, 0, length);
    	}

    	//Close the streams
    	output.flush();
    	output.close();
    	input.close();
    }
}
