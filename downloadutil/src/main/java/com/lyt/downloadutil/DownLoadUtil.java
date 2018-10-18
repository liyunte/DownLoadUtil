package com.lyt.downloadutil;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.v4.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;


/**
 * ========================================
 * 作 者：liyunte
 * <p/>
 * <p/>
 * 版 本：1.0
 * <p/>
 * 创建日期：2018/3/12 11:23
 * <p/>
 * 描 述：版本更新工具类
 * <p/>
 * <p/>
 * 在当前activity中：
 *
 * @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
 * super.onActivityResult(requestCode, resultCode, data);
 * if (requestCode==100){
 * DownLoadUtil.installApp(mContext);
 * }
 * }
 * <p>
 * 修订历史：
 * <p/>
 * ========================================
 */
@SuppressWarnings("ALL")
public class DownLoadUtil {
    private DownloadManager mDownloadManager;
    private long downloadId;
    private String apkName = "play.apk";
    private WeakReference<Activity> weakReference;
    public static final int REQUEST_CODE = 100;

    public DownLoadUtil(Activity context) {
        weakReference = new WeakReference<>(context);
    }

    public void download(String url, String apkName) {
        File file_directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(file_directory, apkName);
        FileUtil.delete(file.getAbsolutePath());
        final Activity mContext = weakReference.get();
        if (mContext == null) {
            return;
        }
        this.apkName = apkName;
        final String packageName = "com.android.providers.downloads";
        int state = mContext.getPackageManager().getApplicationEnabledSetting(packageName);
        //检测下载管理器是否被禁用
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext).setTitle("温馨提示").setMessage
                    ("系统下载管理器被禁止，需手动打开").setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + packageName));
                        mContext.startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
                        mContext.startActivity(intent);
                    }
                }
            }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.create().show();
        } else {
            //正常下载流程
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setAllowedOverRoaming(false);

            //通知栏显示
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setTitle("正在下载");
            request.setVisibleInDownloadsUi(true);
            //设置下载的路径
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, apkName);
            //获取DownloadManager
            mDownloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            downloadId = mDownloadManager.enqueue(request);
            mContext.registerReceiver(mReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            checkStatus();
        }
    };

    /**
     * 检查下载状态
     */
    private void checkStatus() {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        Cursor cursor = mDownloadManager.query(query);
        if (cursor.moveToFirst()) {
            int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
            switch (status) {
                //下载暂停
                case DownloadManager.STATUS_PAUSED:
                    break;
                //下载延迟
                case DownloadManager.STATUS_PENDING:

                    break;
                //正在下载
                case DownloadManager.STATUS_RUNNING:

                    break;
                //下载完成
                case DownloadManager.STATUS_SUCCESSFUL:
                    installProcess();
                    final Activity mContext = weakReference.get();
                    if (mContext == null) {
                        return;
                    }
                    mContext.unregisterReceiver(mReceiver);
                    break;
                //下载失败
                case DownloadManager.STATUS_FAILED:
                    final Activity mContexts = weakReference.get();
                    if (mContexts == null) {
                        return;
                    }
                    mContexts.unregisterReceiver(mReceiver);
                    break;
            }
        }
        cursor.close();
    }

    public static void installApp(Context context, String apkName) {
        File apkFile =
                new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), apkName);
        if (!apkFile.exists()) {
            return;
        }
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            String command = "chmod " + "777" + " " + apkFile;
            Runtime runtime = Runtime.getRuntime();
            try {
                runtime.exec(command);
            } catch (IOException e) {
                e.printStackTrace();
            }
            uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apkFile);
        } else {
            uri = Uri.fromFile(apkFile);
        }
        Intent intent = new Intent();
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.setAction(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }


    private void installProcess() {
        final Activity mContext = weakReference.get();
        if (mContext == null) {
            return;
        }
        boolean haveInstallPermission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //先获取是否有安装未知来源应用的权限
            haveInstallPermission = mContext.getPackageManager().canRequestPackageInstalls();
            if (!haveInstallPermission) {//没有权限
                startInstallPermissionSettingActivity(mContext);
                return;
            }
        }
        installApp(mContext, apkName);
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startInstallPermissionSettingActivity(Activity mContext) {
        Uri packageURI = Uri.parse("package:" + mContext.getPackageName());
        //注意这个是8.0新API
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageURI);
        mContext.startActivityForResult(intent, REQUEST_CODE);
    }
}
