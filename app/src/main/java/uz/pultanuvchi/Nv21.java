package uz.pultanuvchi;

import java.nio.ByteBuffer;

/**
 * Kamera kadrini (YUV_420_888 tekisliklari) NV21 shakliga (Y, so'ng aralash V va U) joylaydi.
 * Android'ga bog'liq emas, shuning uchun alohida sinab ko'rish mumkin.
 */
public final class Nv21 {
    private Nv21() {
    }

    /**
     * @param yPlane        yorqinlik tekisligi
     * @param yRowStride    yorqinlik tekisligining qator uzunligi (bayt)
     * @param uPlane        U (Cb) tekisligi
     * @param vPlane        V (Cr) tekisligi
     * @param uvRowStride   rang tekisliklarining qator uzunligi (bayt)
     * @param uvPixelStride rang tekisliklarida ikki qo'shni qiymat orasidagi masofa (1 yoki 2)
     */
    public static byte[] pack(ByteBuffer yPlane, int yRowStride,
                              ByteBuffer uPlane, ByteBuffer vPlane,
                              int uvRowStride, int uvPixelStride,
                              int width, int height) {
        final int chromaWidth = (width + 1) / 2;
        final int chromaHeight = (height + 1) / 2;
        final byte[] out = new byte[width * height + 2 * chromaWidth * chromaHeight];

        final ByteBuffer y = yPlane.duplicate();
        int pos = 0;
        for (int row = 0; row < height; row++) {
            int start = row * yRowStride;
            int len = Math.min(width, y.limit() - start);
            if (len > 0) {
                y.position(start);
                y.get(out, pos, len);
            }
            pos += width;
        }

        final ByteBuffer u = uPlane.duplicate();
        final ByteBuffer v = vPlane.duplicate();
        for (int row = 0; row < chromaHeight; row++) {
            for (int col = 0; col < chromaWidth; col++) {
                int index = row * uvRowStride + col * uvPixelStride;
                out[pos++] = index < v.limit() ? v.get(index) : (byte) 128;
                out[pos++] = index < u.limit() ? u.get(index) : (byte) 128;
            }
        }
        return out;
    }
}
