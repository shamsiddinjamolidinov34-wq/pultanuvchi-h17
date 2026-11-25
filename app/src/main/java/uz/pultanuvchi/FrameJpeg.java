package uz.pultanuvchi;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;

import java.io.ByteArrayOutputStream;

/** Kamera kadrini (YUV_420_888) to'g'ri yo'nalishdagi JPEG rasmga aylantiradi. */
final class FrameJpeg {
    private FrameJpeg() {
    }

    /**
     * @param rotationDegrees kadrni tik holatga keltirish uchun soat strelkasi bo'yicha burish burchagi
     * @return JPEG baytlari; kadr formati mos kelmasa null
     */
    static byte[] fromImage(Image image, int rotationDegrees, int quality) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            return null;
        }
        Image.Plane[] planes = image.getPlanes();
        if (planes.length < 3) {
            return null;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] nv21 = Nv21.pack(
                planes[0].getBuffer(), planes[0].getRowStride(),
                planes[1].getBuffer(), planes[2].getBuffer(),
                planes[1].getRowStride(), planes[1].getPixelStride(),
                width, height);
        return toJpeg(nv21, width, height, rotationDegrees, quality);
    }

    static byte[] toJpeg(byte[] nv21, int width, int height, int rotationDegrees, int quality) {
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        if (!yuv.compressToJpeg(new Rect(0, 0, width, height), quality, out)) {
            return null;
        }
        byte[] jpeg = out.toByteArray();

        int rotation = ((rotationDegrees % 360) + 360) % 360;
        if (rotation == 0) {
            return jpeg;
        }
        Bitmap source = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
        if (source == null) {
            return jpeg;
        }
        Bitmap rotated = null;
        try {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            rotated = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
            ByteArrayOutputStream rotatedOut = new ByteArrayOutputStream(64 * 1024);
            if (!rotated.compress(Bitmap.CompressFormat.JPEG, quality, rotatedOut)) {
                return jpeg;
            }
            return rotatedOut.toByteArray();
        } finally {
            if (rotated != null && rotated != source) {
                rotated.recycle();
            }
            source.recycle();
        }
    }
}
