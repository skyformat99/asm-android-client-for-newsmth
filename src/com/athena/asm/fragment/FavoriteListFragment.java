package com.athena.asm.fragment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;

import com.actionbarsherlock.app.SherlockFragment;
import com.athena.asm.ActivityFragmentTargets;
import com.athena.asm.OnOpenActivityFragmentListener;
import com.athena.asm.R;
import com.athena.asm.aSMApplication;
import com.athena.asm.Adapter.FavoriteListAdapter;
import com.athena.asm.data.Board;
import com.athena.asm.listener.OnKeyDownListener;
import com.athena.asm.util.ListViewUtil;
import com.athena.asm.util.StringUtility;
import com.athena.asm.util.task.EditFavoriteTask;
import com.athena.asm.util.task.LoadFavoriteTask;
import com.athena.asm.viewmodel.BaseViewModel;
import com.athena.asm.viewmodel.HomeViewModel;

public class FavoriteListFragment extends SherlockFragment implements
		BaseViewModel.OnViewModelChangObserver, OnKeyDownListener {

	private HomeViewModel m_viewModel;

	private LayoutInflater m_inflater;

	private ExpandableListView m_listView;
	FavoriteListAdapter m_favoriteListAdapter;

	private boolean m_isLoaded;
	
	private OnOpenActivityFragmentListener m_onOpenActivityFragmentListener;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		setRetainInstance(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		m_inflater = inflater;
		View layout = m_inflater.inflate(R.layout.favorite, null);
		m_listView = (ExpandableListView) layout.findViewById(R.id.favorite_list);

		aSMApplication application = (aSMApplication) getActivity()
				.getApplication();
		m_viewModel = application.getHomeViewModel();
		m_viewModel.registerViewModelChangeObserver(this);
		
		m_isLoaded = false;

		return m_listView;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		Activity parentActivity = getSherlockActivity();
		if (parentActivity instanceof OnOpenActivityFragmentListener) {
			m_onOpenActivityFragmentListener = (OnOpenActivityFragmentListener) parentActivity;
		}

		if (m_viewModel.getCurrentTab() != null
				&& m_viewModel.getCurrentTab().equals(
						StringUtility.TAB_FAVORITE)) {
            reloadFavorite();
		}
	}

	@Override
	public void onDestroy() {
		m_viewModel.unregisterViewModelChangeObserver(this);
		super.onDestroy();
	}

	private void extractSubDirectory(Board board, List<Board> currentBoardList) {
		for (Iterator<Board> iterator = board.getChildBoards().iterator(); iterator
				.hasNext();) {
			Board childBoard = iterator.next();
			if (childBoard.isDirectory()) {
				extractSubDirectory(childBoard, currentBoardList);
			} else {
				currentBoardList.add(childBoard);
			}
		}
	}

	public void reloadFavorite() {
		if (m_viewModel.getFavList() == null) {
			if(m_viewModel.m_isLoadingInProgress) return;
			LoadFavoriteTask loadFavoriteTask = new LoadFavoriteTask(
					getActivity(), m_viewModel);
			loadFavoriteTask.execute();
		} else {
			m_isLoaded = true;

			// list of directories
			List<String> directoryList = new ArrayList<String>();
			// list of boardlist, each boardlist is for a directory in directoryList
			List<List<Board>> listOfBoardList = new ArrayList<List<Board>>();
			// list of boards without parent directory, put it in special 'root' directory
			List<Board> rootBoardList = new ArrayList<Board>();

			// get local copy of favlist
			ArrayList<Board> favList = new ArrayList<Board>();
			favList.addAll(m_viewModel.getFavList());

			// add faked directory for recently viewed boards
			Board fakeBoard = new Board();
			fakeBoard.setDirectory(true);
			fakeBoard.setDirectoryName("最近访问版面");
			fakeBoard.setCategoryName("目录");
			favList.add(fakeBoard);

			for (Iterator<Board> iterator = favList.iterator(); iterator.hasNext();) {
				Board board = iterator.next();
				if (board.isDirectory()) {
					// directory
					directoryList.add(board.getDirectoryName());
					if (board.getDirectoryName().equals("最近访问版面")) {
						// special directory for recently viewed boards
						listOfBoardList.add(new ArrayList<Board>(
								aSMApplication.getCurrentApplication().getRecentBoards()));
					} else {
						// normal directory, find child boards
						List<Board> childBoardList = new ArrayList<Board>();
						extractSubDirectory(board, childBoardList);
						listOfBoardList.add(childBoardList);
					}
				} else {
					// board without parent, add it to rootBoardList
					rootBoardList.add(board);
				}
			}

			if (listOfBoardList.size() > 0) {
				// add special 'root' directory
				directoryList.add(0, "我的收藏夹");
				listOfBoardList.add(0, rootBoardList);
			}

			m_favoriteListAdapter = new FavoriteListAdapter(
					m_inflater, directoryList, listOfBoardList);
			m_listView.setAdapter(m_favoriteListAdapter);

			// expand special 'root' directory by default
			m_listView.expandGroup(0);
			m_listView.setOnChildClickListener(new OnChildClickListener() {

				@Override
				public boolean onChildClick(ExpandableListView parent,
						View view, int groupPosition, int childPosition, long id) {
					Bundle bundle = new Bundle();
					bundle.putSerializable(StringUtility.BOARD, (Board) view.getTag(R.id.tag_second));
					aSMApplication.getCurrentApplication().addRecentBoard((Board) view
							.getTag(R.id.tag_second));

					if (m_onOpenActivityFragmentListener != null) {
						m_onOpenActivityFragmentListener.onOpenActivityOrFragment(ActivityFragmentTargets.SUBJECT_LIST, bundle);
					}
					return false;
				}
			});

            m_listView.setOnItemLongClickListener(new OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int pos, long id) {
                    if (ExpandableListView.getPackedPositionType(id) == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
                        // get selected board
                        final int groupPosition = ExpandableListView.getPackedPositionGroup(id);
                        final int childPosition = ExpandableListView.getPackedPositionChild(id);
                        List<List<Board>> m_boards = m_favoriteListAdapter.getFavoriteBoards();
                        final Board board = m_boards.get(groupPosition).get(childPosition);

                        // confirm dialog
                        Builder builder = new AlertDialog.Builder(getActivity());
                        String title = String.format("将版面\"%s\"从收藏夹中删除么？", board.getChsName());
                        builder.setTitle("收藏夹操作").setMessage(title);

                        builder.setPositiveButton("删除", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String groupid = Integer.toString(groupPosition);
                                EditFavoriteTask task = new EditFavoriteTask(getActivity(), m_viewModel, groupid, board
                                        .getEngName(), board.getBoardID(), EditFavoriteTask.FAVORITE_DELETE);
                                task.execute();
                                dialog.dismiss();
                            }
                        });
                        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                        AlertDialog noticeDialog = builder.create();
                        noticeDialog.show();

                        return true;
                    }
                    return false;
                }
            });
		}
	}

	@Override
	public void onViewModelChange(BaseViewModel viewModel,
			String changedPropertyName, Object... params) {
		if (changedPropertyName.equals(HomeViewModel.FAVLIST_PROPERTY_NAME)) {
			reloadFavorite();
		} else if (changedPropertyName.equals(HomeViewModel.CURRENTTAB_PROPERTY_NAME)) {
			if (!m_isLoaded && m_viewModel.getCurrentTab() != null
					&& m_viewModel.getCurrentTab().equals(
							StringUtility.TAB_FAVORITE)) {
				reloadFavorite();
			}
		}
	}

    @Override
    public boolean onKeyDown(int keyCode) {
        return ListViewUtil.ScrollListViewByKey(m_listView, keyCode);
    }
}
