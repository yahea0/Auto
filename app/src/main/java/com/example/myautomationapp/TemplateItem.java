package com.example.myautomationapp;

import java.io.Serializable;

public class TemplateItem implements Serializable, Cloneable {
    private String id;
    private String imagePath;
    private String name;
    private int similarity = 70;
    private int colorTolerance = 30;
    private boolean ignoreLevelBadge = false;
    private int width = 0;
    private int height = 0;

    // نظام العلامة المميزة المرجعية (Landmark Anchor) لحل مشكلة الخريطة المتحركة في لعبة الفاتحون
    private boolean hasAnchor = false;
    private String anchorImagePath = null;
    private int anchorRelativeX = 0; // المسافة النسبية الأفقية للعلامة عن المعسكر
    private int anchorRelativeY = 0; // المسافة النسبية الرأسية للعلامة عن المعسكر
    private int anchorSimilarity = 75;
    private int anchorTolerance = 35;

    public TemplateItem(String id, String imagePath, String name) {
        this.id = id;
        this.imagePath = imagePath;
        this.name = name;
    }

    public String getId() { return id; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getSimilarity() { return similarity; }
    public void setSimilarity(int similarity) { this.similarity = similarity; }

    public int getColorTolerance() { return colorTolerance; }
    public void setColorTolerance(int colorTolerance) { this.colorTolerance = colorTolerance; }

    public boolean isIgnoreLevelBadge() { return ignoreLevelBadge; }
    public void setIgnoreLevelBadge(boolean ignoreLevelBadge) { this.ignoreLevelBadge = ignoreLevelBadge; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public boolean hasAnchor() { return hasAnchor; }
    public void setHasAnchor(boolean hasAnchor) { this.hasAnchor = hasAnchor; }

    public String getAnchorImagePath() { return anchorImagePath; }
    public void setAnchorImagePath(String anchorImagePath) { this.anchorImagePath = anchorImagePath; }

    public int getAnchorRelativeX() { return anchorRelativeX; }
    public void setAnchorRelativeX(int anchorRelativeX) { this.anchorRelativeX = anchorRelativeX; }

    public int getAnchorRelativeY() { return anchorRelativeY; }
    public void setAnchorRelativeY(int anchorRelativeY) { this.anchorRelativeY = anchorRelativeY; }

    public int getAnchorSimilarity() { return anchorSimilarity; }
    public void setAnchorSimilarity(int anchorSimilarity) { this.anchorSimilarity = anchorSimilarity; }

    public int getAnchorTolerance() { return anchorTolerance; }
    public void setAnchorTolerance(int anchorTolerance) { this.anchorTolerance = anchorTolerance; }

    @Override
    public TemplateItem clone() {
        try {
            return (TemplateItem) super.clone();
        } catch (CloneNotSupportedException e) {
            TemplateItem item = new TemplateItem(this.id, this.imagePath, this.name);
            item.similarity = this.similarity;
            item.colorTolerance = this.colorTolerance;
            item.ignoreLevelBadge = this.ignoreLevelBadge;
            item.width = this.width;
            item.height = this.height;
            item.hasAnchor = this.hasAnchor;
            item.anchorImagePath = this.anchorImagePath;
            item.anchorRelativeX = this.anchorRelativeX;
            item.anchorRelativeY = this.anchorRelativeY;
            item.anchorSimilarity = this.anchorSimilarity;
            item.anchorTolerance = this.anchorTolerance;
            return item;
        }
    }
}

الخطوة 124: [✏️ ملف موجود - استبدال محتواه بالكامل]

📍 المسار: app/src/main/res/layout/dialog_template_settings.xml
(دعم تعديل اسم النموذج + قسم العلامة المميزة المرجعية مع زر التقاطها + زر
التحديد بالمستطيل):

<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="320dp"
    android:layout_height="wrap_content"
    app:cardBackgroundColor="#222222"
    app:cardCornerRadius="16dp"
    app:cardElevation="20dp">

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:maxHeight="540dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <!-- تعديل اسم النموذج -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Template Name (اسم النموذج):"
                android:textColor="#00E5FF"
                android:textSize="13sp"
                android:textStyle="bold" />
            <EditText
                android:id="@+id/etTemplateCustomName"
                android:layout_width="match_parent"
                android:layout_height="40dp"
                android:textColor="#FFFFFF"
                android:background="#333333"
                android:paddingStart="8dp"
                android:layout_marginBottom="10dp" />

            <!-- عارض الصورة المباشر مع زر إعادة التأطير بالمستطيل -->
            <FrameLayout
                android:layout_width="match_parent"
                android:layout_height="120dp"
                android:background="#181818"
                android:layout_marginBottom="10dp">

                <ImageView
                    android:id="@+id/ivSettingsTemplatePreview"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:scaleType="fitCenter" />

                <TextView
                    android:id="@+id/tvLiveTrimDimensions"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_gravity="bottom|end"
                    android:text="[100 x 100 px]"
                    android:textColor="#00E5FF"
                    android:textSize="11sp"
                    android:background="#88000000"
                    android:padding="3dp" />

                <!-- زر إعادة فتح المستطيل المطاطي لتعديل الإطار يدوياً -->
                <ImageView
                    android:id="@+id/btnReCropRubberFrame"
                    android:layout_width="32dp"
                    android:layout_height="32dp"
                    android:layout_gravity="top|end"
                    android:src="@android:drawable/ic_menu_crop"
                    android:padding="4dp"
                    android:background="#88000000"
                    app:tint="#00E5FF" />
            </FrameLayout>

            <!-- 1. نسبة التطابق -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Similarity % (نسبة التطابق المطلوبة):"
                android:textColor="#FFFFFF"
                android:textSize="13sp" />
            <EditText
                android:id="@+id/etTemplateSim"
                android:layout_width="match_parent"
                android:layout_height="40dp"
                android:inputType="number"
                android:textColor="#00E5FF"
                android:background="#333333"
                android:paddingStart="8dp"
                android:layout_marginBottom="10dp" />

            <!-- 2. تساهل الإضاءة وأشعة الشمس -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Light &amp; Color Tolerance (تساهل الإضاءة):"
                android:textColor="#FFFFFF"
                android:textSize="13sp" />
            <EditText
                android:id="@+id/etTemplateTolerance"
                android:layout_width="match_parent"
                android:layout_height="40dp"
                android:inputType="number"
                android:hint="20 - 50 (افتراضي 30)"
                android:textColor="#00E5FF"
                android:background="#333333"
                android:paddingStart="8dp"
                android:layout_marginBottom="10dp" />

            <!-- 3. عزل الشارات والأرقام المتغيرة -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:background="#2E2E2E"
                android:padding="8dp"
                android:layout_marginBottom="10dp">
                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="Ignore Variable Badge\n(تجاهل الشارات والأرقام المتغيرة)"
                    android:textColor="#FFFFFF"
                    android:textSize="12sp"
                    android:textStyle="bold" />
                <Switch
                    android:id="@+id/swIgnoreLevelBadge"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <!-- 4. تعديل البكسل (+/- 1px) القابل للتراجع والعكس بحرية -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Pixel Trim (+/- 1px تعديل الحواف بالبكسل):"
                android:textColor="#C084FC"
                android:textSize="13sp"
                android:textStyle="bold"
                android:paddingBottom="4dp" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="38dp"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:layout_marginBottom="14dp">

                <Button
                    android:id="@+id/btnTrimWMinus"
                    android:layout_width="0dp"
                    android:layout_height="match_parent"
                    android:layout_weight="1"
                    android:text="W -1"
                    android:textSize="11sp"
                    android:backgroundTint="#3A3A3A" />
                <Button
                    android:id="@+id/btnTrimWPlus"
                    android:layout_width="0dp"
                    android:layout_height="match_parent"
                    android:layout_weight="1"
                    android:text="W +1"
                    android:textSize="11sp"
                    android:backgroundTint="#3A3A3A"
                    android:layout_marginStart="4dp" />
                <Button
                    android:id="@+id/btnTrimHMinus"
                    android:layout_width="0dp"
                    android:layout_height="match_parent"
                    android:layout_weight="1"
                    android:text="H -1"
                    android:textSize="11sp"
                    android:backgroundTint="#3A3A3A"
                    android:layout_marginStart="4dp" />
                <Button
                    android:id="@+id/btnTrimHPlus"
                    android:layout_width="0dp"
                    android:layout_height="match_parent"
                    android:layout_weight="1"
                    android:text="H +1"
                    android:textSize="11sp"
                    android:backgroundTint="#3A3A3A"
                    android:layout_marginStart="4dp" />
            </LinearLayout>

            <!-- 5. قسم العلامة المميزة المرجعية (Landmark Anchor) لحل مشكلة الخريطة المتحركة -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:background="#1E293B"
                android:padding="10dp"
                android:layout_marginBottom="14dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical">
                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="Landmark Anchor (العلامة المميزة المرجعية)"
                        android:textColor="#38BDF8"
                        android:textSize="12sp"
                        android:textStyle="bold" />
                    <Switch
                        android:id="@+id/swEnableAnchor"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>

                <TextView
                    android:id="@+id/tvAnchorStatusDesc"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="يمنع النقر إلا إذا وُجدت علامة مميزة قريبة (صخرة/جدار) بنفس الموضع النسبي."
                    android:textColor="#94A3B8"
                    android:textSize="10sp"
                    android:paddingTop="2dp"
                    android:paddingBottom="6dp" />

                <Button
                    android:id="@+id/btnCaptureLandmarkAnchor"
                    android:layout_width="match_parent"
                    android:layout_height="38dp"
                    android:text="📷 التقاط العلامة المميزة بالمستطيل"
                    android:backgroundTint="#0284C7"
                    android:textColor="#FFFFFF"
                    android:textSize="12sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <Button
                android:id="@+id/btnSaveTemplateSettings"
                android:layout_width="match_parent"
                android:layout_height="44dp"
                android:text="SAVE SETTINGS"
                android:backgroundTint="#00E5FF"
                android:textColor="#121212"
                android:textStyle="bold" />

        </LinearLayout>
    </ScrollView>
</androidx.cardview.widget.CardView>
