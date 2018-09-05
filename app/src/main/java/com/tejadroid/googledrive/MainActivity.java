package com.tejadroid.googledrive;

import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.OpenFileActivityOptions;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.tejadroid.googledrive.utils.ActivityUtil;
import com.tejadroid.googledrive.utils.Constant;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import droidninja.filepicker.FilePickerBuilder;
import droidninja.filepicker.FilePickerConst;
import droidninja.filepicker.models.sort.SortingTypes;
import droidninja.filepicker.utils.Orientation;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

import static com.tejadroid.googledrive.utils.Constant.REQUEST_CODE_OPEN_ITEM;
import static com.tejadroid.googledrive.utils.Constant.REQUEST_CODE_PICKFILE;
import static com.tejadroid.googledrive.utils.Constant.REQUEST_CODE_SIGN_IN;

public class MainActivity extends AppCompatActivity implements
        EasyPermissions.PermissionCallbacks {

    public static final int RC_FILE_PICKER_PERM = 321;

    private GoogleSignInAccount signInAccount;
    private Set<Scope> requiredScopes;
    private DriveClient mDriveClient;
    private DriveResourceClient mDriveResourceClient;

    private OpenFileActivityOptions openOptions;
    private TaskCompletionSource<DriveId> mOpenItemTaskSource;


    private boolean isGoogleLoginSuccess = false;
    private boolean isUpload = false;
    private boolean isPermissionApprove = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initialize();
        signInAccount = GoogleSignIn.getLastSignedInAccount(this);

        if (signInAccount != null && signInAccount.getGrantedScopes().containsAll(requiredScopes)) {
            initializeDriveClient(signInAccount);
            isGoogleLoginSuccess = true;
        } else {
            isGoogleLoginSuccess = false;
        }

        findViewById(R.id.btn_download_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isUpload = false;
                if (isGoogleLoginSuccess) {
                    onDriveClientReady();
                } else {
                    signIn();
                }
            }
        });

        findViewById(R.id.btn_upload_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isUpload = true;
                if (isGoogleLoginSuccess) {
                    pickUploadFile();
                } else {
                    signIn();
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_SIGN_IN:
                isGoogleLoginSuccess = false;
                if (resultCode == RESULT_OK) {
                    Task<GoogleSignInAccount> getAccountTask = GoogleSignIn.getSignedInAccountFromIntent(data);
                    if (getAccountTask.isSuccessful()) {
                        initializeDriveClient(getAccountTask.getResult());
                        isGoogleLoginSuccess = true;
                        ActivityUtil.showToast(this, "Sign in successfully.");
                        if (isUpload) {
                            pickUploadFile();
                        } else {
                            onDriveClientReady();
                        }
                    } else {
                        ActivityUtil.showToast(this, "Sign in failed.");
                    }
                } else {
                    ActivityUtil.showToast(this, "Sign in failed.");
                }
                break;
            case REQUEST_CODE_PICKFILE:
                getDriveFolder();
                break;

            case FilePickerConst.REQUEST_CODE_DOC:
                if (resultCode == RESULT_OK && data != null) {
                    docPaths.addAll(data.getStringArrayListExtra(FilePickerConst.KEY_SELECTED_DOCS));
                    if (docPaths != null && docPaths.size() > 0) {
                        uploadFile = docPaths.get(0);
                        if (!uploadFile.isEmpty()) {
                            getDriveFolder();
                        }
                    }
                }
                break;
            case REQUEST_CODE_OPEN_ITEM:
//                mOpenItemTaskSource = new TaskCompletionSource<>();
                if (resultCode == RESULT_OK) {
                    DriveId driveId = data.getParcelableExtra(OpenFileActivityOptions.EXTRA_RESPONSE_DRIVE_ID);
                    mOpenItemTaskSource.setResult(driveId);
                } else {
                    mOpenItemTaskSource.setException(new RuntimeException("Unable to open file"));
                }
                break;
        }
    }


    /**
     * Initialize Google Drive Open File Setup
     * Here you can access msword, pdf and document file from google drive.
     */
    private void initialize() {

        requiredScopes = new HashSet<>(2);
        requiredScopes.add(Drive.SCOPE_FILE);
        requiredScopes.add(Drive.SCOPE_APPFOLDER);

        List<String> mimeType = new ArrayList<>();
        mimeType.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        mimeType.add("application/msword");
        mimeType.add("application/pdf");

        openOptions = new OpenFileActivityOptions.Builder()
                .setMimeType(mimeType)
                .setActivityTitle("Select file")
                .build();
    }


    /*
        Initialize Google Drive client for Drive Resource Access
     */
    private void initializeDriveClient(GoogleSignInAccount signInAccount) {
        mDriveClient = Drive.getDriveClient(getApplicationContext(), signInAccount);
        mDriveResourceClient = Drive.getDriveResourceClient(getApplicationContext(), signInAccount);
    }


    /**
     * Sign in Google Drive with request scope of drive file
     */
    private void signIn() {
        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(Drive.SCOPE_FILE)
                .requestScopes(Drive.SCOPE_APPFOLDER)
                .build();
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, signInOptions);
        startActivityForResult(googleSignInClient.getSignInIntent(), REQUEST_CODE_SIGN_IN);
    }

    @AfterPermissionGranted(RC_FILE_PICKER_PERM)
    private void onDriveClientReady() {
        mOpenItemTaskSource = new TaskCompletionSource<>();
        mDriveClient.newOpenFileActivityIntentSender(openOptions)
                .continueWith(new Continuation<IntentSender, Void>() {
                    @Override
                    public Void then(@NonNull Task<IntentSender> task) throws Exception {
                        startIntentSenderForResult(
                                task.getResult(), REQUEST_CODE_OPEN_ITEM, null, 0, 0, 0);
                        return null;
                    }
                });


        Task<DriveId> tasks = mOpenItemTaskSource.getTask();
        tasks.addOnSuccessListener(this,
                new OnSuccessListener<DriveId>() {
                    @Override
                    public void onSuccess(DriveId driveId) {
                        final DriveFile driveFile = driveId.asDriveFile();
                        Task<Metadata> getMetadataTask = mDriveResourceClient.getMetadata(driveFile);
                        getMetadataTask
                                .addOnSuccessListener(new OnSuccessListener<Metadata>() {
                                    @Override
                                    public void onSuccess(Metadata metadata) {
                                        Log.e(Constant.TAG, "Drive File Name : " + metadata.getTitle());
                                        retrieveContents(driveFile, metadata.getTitle());
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.e(Constant.TAG, "Unable to retrieve metadata", e);
                                    }
                                });
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        ActivityUtil.showToast(MainActivity.this, "File not selected.");
                    }
                });
    }

    private void retrieveContents(final DriveFile file, final String file_name) {

        // [START open_file]
        final Task<DriveContents> openFileTask = mDriveResourceClient.openFile(file, DriveFile.MODE_READ_ONLY);
        // [END open_file]

        // [START read_contents]
        openFileTask.continueWithTask(new Continuation<DriveContents, Task<Void>>() {
            @Override
            public Task<Void> then(@NonNull Task<DriveContents> task) throws Exception {

                DriveContents contents = task.getResult();

                InputStream input = contents.getInputStream();


                String extension = file_name.substring(file_name.lastIndexOf("."));
                ActivityUtil.showToast(MainActivity.this, "Downloading " + extension + "...");

                final File dFile = new File(Constant.DOWNLOAD_PATH, "" + file_name);
                if (!dFile.exists()) {
                    dFile.createNewFile();
                }
                OutputStream output = new FileOutputStream(dFile);

                try {
                    byte[] buffer = new byte[4 * 1024]; // or other buffer size
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    output.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        output.close();
                        input.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                ActivityUtil.showToast(MainActivity.this, "Download file successfully.");
                return mDriveResourceClient.discardContents(contents);
            }
        })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        ActivityUtil.showToast(MainActivity.this, "Unable to download file.");
                    }
                });
        // [END read_contents]
    }


    ArrayList<String> docPaths = new ArrayList<>();
    String uploadFile;

    @AfterPermissionGranted(RC_FILE_PICKER_PERM)
    private void pickUploadFile() {
//        FilePickerBuilder.getInstance().setMaxCount(1)
//                .setSelectedFiles(filePaths)
//                .setActivityTheme(R.style.AppTheme)
//                .pickFile(MainActivity.this);

        if (EasyPermissions.hasPermissions(this, FilePickerConst.PERMISSIONS_FILE_PICKER)) {
            onPickDoc();
        } else {
            // Ask for one permission
            EasyPermissions.requestPermissions(this, "We need this pemission to read documents from device.",
                    Constant.REQUEST_CODE_PICKFILE, FilePickerConst.PERMISSIONS_FILE_PICKER);
        }
    }

    public void onPickDoc() {
        String[] zips = {".zip", ".rar"};
        String[] pdfs = {".pdf"};
        int maxCount = 1;
        /*if ((docPaths.size()) == 1) {
            Toast.makeText(this, "Cannot select more than " + 1 + " items",
                    Toast.LENGTH_SHORT).show();
        } else {*/
        docPaths = new ArrayList<>();
        FilePickerBuilder.getInstance()
                .setMaxCount(maxCount)
                .setSelectedFiles(docPaths)
                .setActivityTitle("Please select doc")
//                    .addFileSupport("PDF", pdfs)
                .enableDocSupport(true)
                .enableSelectAll(true)
                .sortDocumentsBy(SortingTypes.name)
                .withOrientation(Orientation.UNSPECIFIED)
                .pickFile(this);
        /*}*/
    }

    private void getDriveFolder() {
        mDriveResourceClient
                .getRootFolder()
                .continueWithTask(new Continuation<DriveFolder, Task<DriveFolder>>() {
                    @Override
                    public Task<DriveFolder> then(@NonNull Task<DriveFolder> task)
                            throws Exception {
                        return task;
                    }
                })
                .addOnSuccessListener(MainActivity.this,
                        new OnSuccessListener<DriveFolder>() {
                            @Override
                            public void onSuccess(final DriveFolder driveFolder) {
                                try {
                                    DriveFolder existFolder = search(driveFolder);
                                    if (existFolder == null) {
                                        createFolder();
                                    } else {
//                                        deleteFolder(existFolder);
                                        uploadSheet(existFolder);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        })
                .addOnFailureListener(MainActivity.this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(Constant.TAG, "Unable to create file", e);
                    }
                });
    }

    private DriveFolder search(DriveFolder driveFolder) throws Exception {
        Query query = new Query.Builder().addFilter(Filters.eq(SearchableField.TITLE, "DroidDrive")).build();

        Task<MetadataBuffer> metadataBufferTask = mDriveResourceClient.queryChildren(driveFolder, query);
        while (!metadataBufferTask.isComplete()
                || !metadataBufferTask.isSuccessful()) {
            Thread.sleep(529);
        }

        MetadataBuffer metadataBuffer = metadataBufferTask.getResult();

        if (metadataBuffer != null && metadataBuffer.getCount() > 0) {
            Log.e(Constant.TAG, "Found " + metadataBuffer.getCount() + " existing folders");
            Metadata metadata = metadataBuffer.get(0);
            if (metadata != null && metadata.isFolder()) {
                Log.e(Constant.TAG, "Returning existing folder");
                return metadata.getDriveId().asDriveFolder();
            } else {
                Log.e(Constant.TAG, "Returning created folder even though we found meta data");
                return null;
            }
        } else {
            Log.e(Constant.TAG, "Returning created folder");
            return null;
        }
    }

    private void deleteFolder(DriveFolder folder) {
        // [START delete_file]
        mDriveResourceClient
                .delete(folder)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.e(Constant.TAG, "Delete folder success");
                        createFolder();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(Constant.TAG, "Delete folder exception : " + e);
                    }
                });


        // [END delete_file]
    }

    private void createFolder() {
        mDriveResourceClient
                .getRootFolder()
                .continueWithTask(new Continuation<DriveFolder, Task<DriveFolder>>() {
                    @Override
                    public Task<DriveFolder> then(@NonNull Task<DriveFolder> task)
                            throws Exception {
                        DriveFolder parentFolder = task.getResult();
                        MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                .setTitle("DroidDrive")
                                .setMimeType(DriveFolder.MIME_TYPE)
                                .setStarred(true)
                                .build();
                        return mDriveResourceClient.createFolder(parentFolder, changeSet);
                    }
                })
                .addOnSuccessListener(MainActivity.this,
                        new OnSuccessListener<DriveFolder>() {
                            @Override
                            public void onSuccess(DriveFolder driveFolder) {
                                Log.e(Constant.TAG, driveFolder.getDriveId().encodeToString());
                                uploadSheet(driveFolder);
                            }
                        })
                .addOnFailureListener(MainActivity.this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(Constant.TAG, "Unable to create file", e);
                    }
                });
    }

    public static String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }

    private void uploadSheet(DriveFolder driveFolder) {
//        File file = new File(Constant.UPLOAD_PATH, "APRCalculator.xls");
        File file = new File(uploadFile);
        if (file.exists()) {
            String mimeType = getMimeType(uploadFile);
            Log.e("FILE", String.valueOf(file.exists()));
//            createFile(driveFolder, file, "application/vnd.ms-excel");
            createFile(driveFolder, file, mimeType);
        }
    }

    private void createFile(final DriveFolder parent, final File file, final String mimeType) {
        // [START create_file]
        final Task<DriveFolder> rootFolderTask = mDriveResourceClient.getRootFolder();
        final Task<DriveContents> createContentsTask = mDriveResourceClient.createContents();
        Tasks.whenAll(rootFolderTask, createContentsTask)
                .continueWithTask(new Continuation<Void, Task<DriveFile>>() {
                    @Override
                    public Task<DriveFile> then(@NonNull Task<Void> task) throws Exception {
                        DriveContents contents = createContentsTask.getResult();
                        OutputStream outputStream = contents.getOutputStream();
                        InputStream in = new FileInputStream(file);

                        try {
                            // Transfer bytes from in to out
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                outputStream.write(buf, 0, len);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            outputStream.close();
                            in.close();
                        }

                        MetadataChangeSet metadataChangeSet =
                                new MetadataChangeSet.Builder()
                                        .setMimeType(mimeType)
                                        .setTitle(file.getName())
                                        .build();

                        return mDriveResourceClient.createFile(parent, metadataChangeSet, contents);
                    }
                })
                .addOnSuccessListener(MainActivity.this,
                        new OnSuccessListener<DriveFile>() {
                            @Override
                            public void onSuccess(DriveFile driveFile) {
                                Log.e(Constant.TAG, "Success :" + driveFile.getDriveId().getResourceId());
                                Toast.makeText(MainActivity.this, "Sheet Uploaded", Toast.LENGTH_SHORT).show();
                            }
                        })
                .addOnFailureListener(MainActivity.this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(Constant.TAG, "Unable to create file", e);
                    }
                });

    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        ActivityUtil.showToast(MainActivity.this, "Permission Granted");
        if (isUpload) {
            pickUploadFile();
        } else {
            onDriveClientReady();
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            new AppSettingsDialog.Builder(this).build().show();
        }
    }
}