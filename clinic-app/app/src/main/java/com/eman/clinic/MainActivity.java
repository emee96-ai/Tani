package com.eman.clinic;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout content;
    private final List<Patient> patients = new ArrayList<>();
    private int nextCard = 1001;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        seed();
        showHome();
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(Color.rgb(21,32,30));
        t.setPadding(16,10,16,10); t.setGravity(Gravity.RIGHT);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this); b.setText(label); b.setOnClickListener(l);
        b.setAllCaps(false); b.setTextSize(16); return b;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextSize(16);
        e.setPadding(20,12,20,12); return e;
    }

    private void shell(String title) {
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(28,28,28,28); content.setBackgroundColor(Color.rgb(247,248,250));
        content.addView(text(title, 28, true));
        TextView sub = text("نظام العيادة • يعمل محلياً ويمكن ربطه بـ Supabase", 14, false);
        sub.setTextColor(Color.rgb(89,102,100)); content.addView(sub);
        scroll.addView(content); setContentView(scroll);
    }

    private void showHome() {
        shell("العيادة");
        content.addView(text("إدارة اليوم", 19, true));
        content.addView(button("👥 تسجيل مريض / الطابور", v -> showQueue()));
        content.addView(button("🩺 شاشة الطبيب", v -> showDoctor()));
        content.addView(button("💳 الحسابات وإغلاق اليوم", v -> showFinance()));
        content.addView(button("📋 سجل المرضى", v -> showPatients()));
        content.addView(text("الحالة: Offline-ready • النسخة 1.0", 14, false));
    }

    private void back() { content.addView(button("← الرئيسية", v -> showHome()), 0); }

    private void showQueue() {
        shell("الطابور والتسجيل"); back();
        final EditText name = input("اسم المريض");
        final EditText phone = input("رقم الهاتف"); phone.setInputType(InputType.TYPE_CLASS_PHONE);
        Spinner type = new Spinner(this);
        String[] types = {"مريض جديد", "مقابلة قريبة مجانية", "مقابلة بعيدة", "نتيجة فحوصات"};
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types));
        content.addView(name); content.addView(phone); content.addView(type);
        content.addView(button("إضافة للطابور", v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) { Toast.makeText(this,"اكتبي اسم المريض",Toast.LENGTH_SHORT).show(); return; }
            Patient p = new Patient(nextCard++, n, phone.getText().toString().trim(), types[type.getSelectedItemPosition()]);
            patients.add(p); Toast.makeText(this,"تمت الإضافة • كرت رقم " + p.cardNo,Toast.LENGTH_LONG).show(); showQueue();
        }));
        content.addView(text("طابور اليوم", 20, true));
        for (Patient p : patients) {
            if (!p.closed) {
                content.addView(text("#"+p.cardNo+"  "+p.name+"\n"+p.visitType+" • "+p.status, 16, true));
                if (p.status.equals("مسجل")) content.addView(button("إرسال للطبيب — #"+p.cardNo, v -> { p.status="بانتظار الطبيب"; showQueue(); }));
            }
        }
    }

    private void showDoctor() {
        shell("شاشة الطبيب"); back();
        Patient active = null;
        for (Patient p: patients) if (!p.closed && p.status.equals("بانتظار الطبيب")) { active=p; break; }
        if (active == null) { content.addView(text("لا يوجد مريض بانتظار الطبيب حالياً.",17,true)); return; }
        final Patient p = active;
        content.addView(text("الكرت #"+p.cardNo+" — "+p.name, 22, true));
        content.addView(text(p.visitType, 15, false));
        EditText symptoms=input("الشكوى والأعراض"); EditText exam=input("الفحص السريري");
        EditText diagnosis=input("التشخيص"); EditText labs=input("الفحوصات المطلوبة");
        EditText treatment=input("العلاج / الروشتة"); EditText follow=input("خطة المتابعة");
        content.addView(symptoms); content.addView(exam); content.addView(diagnosis); content.addView(labs); content.addView(treatment); content.addView(follow);
        content.addView(button("حفظ وإنهاء الكشف", v -> {
            p.symptoms=symptoms.getText().toString(); p.exam=exam.getText().toString(); p.diagnosis=diagnosis.getText().toString();
            p.labs=labs.getText().toString(); p.treatment=treatment.getText().toString(); p.followUp=follow.getText().toString();
            p.status="مكتمل"; p.closed=true; p.completedAt=now();
            Toast.makeText(this,"تم إغلاق الزيارة",Toast.LENGTH_SHORT).show(); showDoctor();
        }));
    }

    private void showFinance() {
        shell("الحسابات اليومية"); back();
        int total=0, waived=0, paid=0;
        for (Patient p: patients) {
            if (p.visitType.contains("مجانية")) waived++; else { total += 10000; if (p.paid) paid += 10000; }
        }
        content.addView(text("المستحق اليوم: "+total+" ج.س",20,true));
        content.addView(text("المحصّل: "+paid+" ج.س",18,true));
        content.addView(text("المقابلات المعفاة: "+waived,16,false));
        for (Patient p: patients) if (!p.visitType.contains("مجانية") && !p.paid) {
            content.addView(text("#"+p.cardNo+" — "+p.name+" • 10,000 ج.س",15,true));
            content.addView(button("تسجيل الدفع", v -> { p.paid=true; showFinance(); }));
        }
        content.addView(button("إغلاق اليوم", v -> Toast.makeText(this,"تم حفظ لقطة إغلاق اليوم محلياً",Toast.LENGTH_LONG).show()));
    }

    private void showPatients() {
        shell("سجل المرضى"); back();
        for (Patient p: patients) {
            String s = "#"+p.cardNo+" — "+p.name+"\n"+p.visitType+" • "+p.status;
            if (!p.diagnosis.isEmpty()) s += "\nالتشخيص: "+p.diagnosis;
            if (!p.treatment.isEmpty()) s += "\nالعلاج: "+p.treatment;
            content.addView(text(s,16,true));
        }
    }

    private String now() { return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()); }
    private void seed() {
        Patient p = new Patient(nextCard++, "أحمد محمد", "", "مريض جديد"); p.status="بانتظار الطبيب"; patients.add(p);
    }

    static class Patient {
        int cardNo; String name, phone, visitType, status="مسجل", symptoms="",exam="",diagnosis="",labs="",treatment="",followUp="",completedAt="";
        boolean closed=false, paid=false;
        Patient(int c,String n,String ph,String vt){cardNo=c;name=n;phone=ph;visitType=vt;}
    }
}
