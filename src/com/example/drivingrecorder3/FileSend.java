package com.example.drivingrecorder3;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.net.Socket;

public class FileSend {
	public static void send(String file, String server)
    {
        BufferedOutputStream bos;
        //String file="D:\\filename.mp4";

        try {
            Socket sock=new Socket(server, 12345);
            bos= new BufferedOutputStream(sock.getOutputStream());
            FileInputStream fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            int n=-1;
            byte[] buffer = new byte[8192];
            while((n = bis.read(buffer))>-1)
            { 
                bos.write(buffer,0,n);
                System.out.println("bytes="+n);
                bos.flush();
            }
            bis.close();
            sock.close();
            
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
}
