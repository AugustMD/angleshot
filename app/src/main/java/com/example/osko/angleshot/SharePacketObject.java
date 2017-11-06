package com.example.osko.angleshot;

import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;

public class SharePacketObject implements Serializable {
    String name;
    byte[] image;

    public SharePacketObject(Bitmap bitmap, String name) {
        this.name = name;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        image = out.toByteArray();
    }

    public byte[] getByteArray() {
        return image;
    }

    public String getPhotoName() {
        return name;
    }
}
