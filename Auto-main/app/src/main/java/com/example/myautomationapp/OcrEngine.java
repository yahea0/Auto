package com.example.myautomationapp;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class OcrEngine {
    private static TextRecognizer recognizer = null;

    private static synchronized TextRecognizer getRecognizer() {
        if (recognizer == null) {
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        }
        return recognizer;
    }

    /**
     * البحث عن النص داخل منطقة الفحص المحددة وإرجاع مركز الكلمة للنقر عليها
     */
    public static Point findTextInRegion(Bitmap screen, Action action) {
        if (screen == null || action == null || action.getTargetText() == null || action.getTargetText().trim().isEmpty()) {
            return null;
        }

        Rect roi = VisionEngine.getEffectiveSearchRegion(action, screen.getWidth(), screen.getHeight());
        if (roi.width() <= 0 || roi.height() <= 0) return null;

        Bitmap crop = Bitmap.createBitmap(screen, roi.left, roi.top, roi.width(), roi.height());

        try {
            InputImage image = InputImage.fromBitmap(crop, 0);
            Text result = Tasks.await(getRecognizer().process(image));

            String search = action.getTargetText().trim();
            for (Text.TextBlock block : result.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    boolean match = action.isTextCaseSensitive() 
                            ? line.getText().contains(search)
                            : line.getText().toLowerCase().contains(search.toLowerCase());

                    if (match && line.getBoundingBox() != null) {
                        Rect box = line.getBoundingBox();
                        int clickX = roi.left + box.centerX() + action.getOffsetX();
                        int clickY = roi.top + box.centerY() + action.getOffsetY();
                        crop.recycle();
                        return new Point(clickX, clickY);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (crop != null && !crop.isRecycled()) {
                crop.recycle();
            }
        }
        return null;
    }
}
