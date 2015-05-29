package cn.iam007.pic.clean.master.recycler;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;

import java.io.File;

import cn.iam007.pic.clean.master.Constants;
import cn.iam007.pic.clean.master.R;
import cn.iam007.pic.clean.master.delete.DeleteRecyclerConfirmDialog;
import cn.iam007.pic.clean.master.utils.ImageUtils;
import cn.iam007.pic.clean.master.utils.SharedPreferenceUtil;

public class RecyclerFragment extends Fragment {
    private View mRootView = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
        getActivity().setTitle(R.string.recycle);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        boolean needScan = false;
        if (mRootView == null) {
            mRootView = inflater.inflate(R.layout.fragment_recycler, null);
            initView(mRootView);
            needScan = true;
        }

        if (SharedPreferenceUtil.getBoolean(SharedPreferenceUtil.HAS_DELETE_SOME_DUPLICATE_IMAGE,
                false)) {
            mRecyclerImageAdapter.clear();
            SharedPreferenceUtil.setBoolean(SharedPreferenceUtil.HAS_DELETE_SOME_DUPLICATE_IMAGE,
                    false);
            needScan = true;
        }

        if (needScan) {
            startScanRecycler();
        }

        if (mRecyclerImageAdapter.getSelectedItem() <= 0) {
            mRestoreBtn.setEnabled(false);
            mDeleteBtn.setEnabled(false);
        } else {
            mRestoreBtn.setEnabled(true);
            mDeleteBtn.setEnabled(true);
        }
        return mRootView;
    }

    private GridLayoutManager mLayoutManager;
    private RecyclerView mRecyclerImageContainer;
    private RecyclerImageAdapter mRecyclerImageAdapter;

    private Button mRestoreBtn;
    private Button mDeleteBtn;

    private void initView(View rootView) {
        mRestoreBtn = (Button) rootView.findViewById(R.id.restore_btn);
        mDeleteBtn = (Button) rootView.findViewById(R.id.delete_btn);
        mDeleteBtn.setOnClickListener(mDeleteBtnClickListener);

        // 初始化recyclerView
        mLayoutManager = new GridLayoutManager(getActivity(), 3);

        mRecyclerImageContainer = (RecyclerView) rootView.findViewById(R.id.recycler_images);
        mRecyclerImageContainer.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mRecyclerImageContainer.setLayoutManager(mLayoutManager);

        mRecyclerImageAdapter = new RecyclerImageAdapter();
        mRecyclerImageContainer.setAdapter(mRecyclerImageAdapter);
    }

    private View.OnClickListener mDeleteBtnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final DeleteRecyclerConfirmDialog dialog =
                    DeleteRecyclerConfirmDialog.builder(getActivity(), mRecyclerImageAdapter.getSelectedItem());
            dialog.getActionButton(DialogAction.POSITIVE).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startToDelete();
                            dialog.dismiss();
                        }
                    });
            dialog.show();
        }
    };

    public void startToDelete() {
        String content = getActivity().getString(R.string.deleting_progress);
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .title(R.string.delete)
                .content(content)
                .theme(Theme.LIGHT)
                .progress(true, 0);

        builder.titleColorRes(R.color.title)
                .dividerColorRes(R.color.divider)
                .positiveColorRes(R.color.red_light_EB5347)
                .negativeColorRes(R.color.black_light_333333)
                .backgroundColorRes(R.color.white_light_FAFAFA);
        final MaterialDialog deleteProgressDialog = builder.build();
        deleteProgressDialog.setCancelable(true);
        try{
            deleteProgressDialog.show();
        } catch(Exception e){
            e.printStackTrace();
        }

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {

            @Override
            public void run() {
                startDeleteTask(deleteProgressDialog);
            }
        }, 1000);

    }

    private Handler mUpdateHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            mRecyclerImageAdapter.notifyDataSetChanged();
            return false;
        }
    });

    private void startDeleteTask(final MaterialDialog progressDialog) {
        new Thread(new Runnable() {
            public void run() {
                mRecyclerImageAdapter.deleteItems();
                progressDialog.dismiss();
                mUpdateHandler.sendEmptyMessage(1);

            }
        }).start();
    }

    private File mRecyclerPath = Constants.getRecyclerPath();

    private void startScanRecycler() {
        RecyclerImageItem item;
        if (mRecyclerPath.isDirectory()) {
            File files[] = mRecyclerPath.listFiles();
            for (File f : files) {
                if (f.isDirectory()) {
                    // 暂时什么都不做
                } else {
                    if (ImageUtils.isImage(f.getName())) {
                        item = new RecyclerImageItem(f.getAbsolutePath(),
                                f.getAbsolutePath(), f.getName());
                        mRecyclerImageAdapter.addItem(item);
                    }
                }
            }
        }
    }

    private boolean mSelectAll = false;
    private MenuItem mSelectAllMenuItem = null;

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.recycler_menu, menu);

        mSelectAllMenuItem = menu.findItem(R.id.action_select_all);

        applySelectState();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_select_all:
                mSelectAll = !mSelectAll;
                applySelectState();
                mRecyclerImageAdapter.selectAll(mSelectAll, mLayoutManager);
                break;

            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void applySelectState() {
        if (mSelectAll) {
            mSelectAllMenuItem.setTitle(R.string.cancel_select_all);
        } else {
            mSelectAllMenuItem.setTitle(R.string.select_all);
        }
    }

    private String SELECTED_RECYCLER_IMAGE_TOTAL_SIZE =
            SharedPreferenceUtil.SELECTED_RECYCLER_IMAGE_TOTAL_SIZE;

    private SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferenceChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {

                @Override
                public void onSharedPreferenceChanged(
                        SharedPreferences sharedPreferences, String key) {
                    if (key.equalsIgnoreCase(SELECTED_RECYCLER_IMAGE_TOTAL_SIZE)) {
                        if (mDeleteBtn != null) {
                            long count = sharedPreferences.getLong(key, 0);
                            if (count <= 0) {
                                mRestoreBtn.setEnabled(false);
                                mDeleteBtn.setEnabled(false);
                            } else {
                                mRestoreBtn.setEnabled(true);
                                mDeleteBtn.setEnabled(true);
                            }
                        }
                    }

                }
            };

    @Override
    public void onResume() {
        super.onResume();

        SharedPreferenceUtil.setOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
    }

    @Override
    public void onPause() {
        super.onPause();

        SharedPreferenceUtil.clearOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
    }
}
