/*
 * Copyright (C) 2023-2024 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.wallpapers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.DisplayMetrics;
import java.io.ByteArrayOutputStream;

public class WallpaperUtils {

    public static Bitmap resizeAndCompress(Bitmap bitmap, Context context) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        context.getDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;
        float maxScale = 1.10f;
        int targetWidth = Math.round(screenWidth * maxScale);
        int targetHeight = Math.round(screenHeight * maxScale);
        if (bitmap.getWidth() != targetWidth || bitmap.getHeight() != targetHeight) {
            bitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        boolean compressed = bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, byteArrayOutputStream);
        if (compressed) {
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
        }
        return bitmap;
    }

    public static Bitmap getDimmedBitmap(Bitmap bitmap, int dimLevel) {
        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        float dimFactor = 1 - (Math.max(0, Math.min(dimLevel, 100)) / 100f);
        Bitmap dimmedBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(dimmedBitmap);
        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setScale(dimFactor, dimFactor, dimFactor, 1.0f);
        ColorMatrixColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(colorFilter);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return dimmedBitmap;
    }

    public static Bitmap getBlurredBitmap(Bitmap bitmap, int radius, Context context) {
        RenderScript rs = RenderScript.create(context);
        float scaleFactor = 0.25f;
        int scaledWidth = Math.round(bitmap.getWidth() * scaleFactor);
        int scaledHeight = Math.round(bitmap.getHeight() * scaleFactor);
        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);
        Bitmap outputBitmap = Bitmap.createBitmap(scaledBitmap.getWidth(), scaledBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        float blurRadius = Math.min(radius, 25);
        int passes = Math.max(1, radius / 25);
        Allocation input = Allocation.createFromBitmap(rs, scaledBitmap);
        Allocation output = Allocation.createFromBitmap(rs, outputBitmap);
        blurScript.setRadius(blurRadius);
        for (int i = 0; i < passes; i++) {
            blurScript.setInput(input);
            blurScript.forEach(output);
            output.copyTo(outputBitmap);
            input.copyFrom(outputBitmap);
        }
        input.destroy();
        output.destroy();
        blurScript.destroy();
        return Bitmap.createScaledBitmap(outputBitmap, bitmap.getWidth(), bitmap.getHeight(), true);
    }
}
