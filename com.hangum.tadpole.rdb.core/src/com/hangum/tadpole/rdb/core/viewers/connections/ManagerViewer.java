/*******************************************************************************
 * Copyright (c) 2013 hangum.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     hangum - initial API and implementation
 ******************************************************************************/
package com.hangum.tadpole.rdb.core.viewers.connections;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.rap.rwt.RWT;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import com.hangum.tadpold.commons.libs.core.define.PublicTadpoleDefine;
import com.hangum.tadpole.commons.sql.define.DBDefine;
import com.hangum.tadpole.dao.ManagerListDTO;
import com.hangum.tadpole.dao.system.UserDBDAO;
import com.hangum.tadpole.dao.system.UserDBResourceDAO;
import com.hangum.tadpole.exception.dialog.ExceptionDetailsErrorDialog;
import com.hangum.tadpole.rdb.core.Activator;
import com.hangum.tadpole.rdb.core.Messages;
import com.hangum.tadpole.rdb.core.actions.connections.QueryEditorAction;
import com.hangum.tadpole.rdb.core.actions.erd.mongodb.MongoDBERDViewAction;
import com.hangum.tadpole.rdb.core.actions.erd.rdb.RDBERDViewAction;
import com.hangum.tadpole.rdb.core.actions.global.ConnectDatabaseAction;
import com.hangum.tadpole.rdb.core.editors.main.MainEditor;
import com.hangum.tadpole.rdb.core.editors.main.MainEditorInput;
import com.hangum.tadpole.rdb.core.util.EditorUtils;
import com.hangum.tadpole.session.manager.SessionManager;
import com.hangum.tadpole.system.TadpoleSystem_UserDBQuery;
import com.hangum.tadpole.system.TadpoleSystem_UserDBResource;
import com.hangum.tadpole.system.permission.PermissionChecker;

/**
 * connection manager 정보를 
 * 
 * @author hangum
 *
 */
public class ManagerViewer extends ViewPart {
	private static final Logger logger = Logger.getLogger(ManagerViewer.class);
	public static String ID = "com.hangum.tadpole.rdb.core.view.connection.manager"; //$NON-NLS-1$
	
	List<ManagerListDTO> treeList = new ArrayList<ManagerListDTO>();
	TreeViewer treeViewer;
	
	public ManagerViewer() {
		super();
	}

	@Override
	public void createPartControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));
		
		treeViewer = new TreeViewer(composite, SWT.VIRTUAL | SWT.BORDER);
		treeViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection is = (IStructuredSelection)event.getSelection();
				Object selElement = is.getFirstElement();
				
				if(selElement instanceof UserDBDAO) {
					addUserResouceData((UserDBDAO)selElement);
				}
			}
		});
		treeViewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				
				IStructuredSelection is = (IStructuredSelection)event.getSelection();
				Object selElement = is.getFirstElement();
				
				// db object를 클릭하면 쿼리 창이 뜨도록하고
				if(selElement instanceof UserDBDAO) {
					QueryEditorAction qea = new QueryEditorAction();
					qea.run((UserDBDAO)selElement);
				// erd를 클릭하면 erd가 오픈되도록 수정 
				} else if(selElement instanceof UserDBResourceDAO) {
					UserDBResourceDAO dao = (UserDBResourceDAO)selElement;
					
					if( PublicTadpoleDefine.RESOURCE_TYPE.ERD.toString().equals(dao.getResource_types())) {
						UserDBDAO userDB = dao.getParent();
						
						if(userDB != null && DBDefine.MONGODB_DEFAULT == DBDefine.getDBDefine(userDB.getDbms_types())) {							
							MongoDBERDViewAction ea = new MongoDBERDViewAction();
							ea.run(dao);
						} else {
							RDBERDViewAction ea = new RDBERDViewAction();
							ea.run(dao);
						}
					} else {
						QueryEditorAction qea = new QueryEditorAction();
						qea.run(dao);
					}
				// manager
				} else if (selElement instanceof ManagerListDTO) {
					ManagerListDTO managerDto = (ManagerListDTO)selElement;
					
					if(PermissionChecker.isShow(SessionManager.representRole())) {
						ConnectDatabaseAction cda = new ConnectDatabaseAction(getSite().getWorkbenchWindow());
						cda.runConnectionDialog(is);
					}
				}
				
				
			}
		});
		Tree tree = treeViewer.getTree();
		tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		tree.setData( RWT.MARKUP_ENABLED, Boolean.TRUE );
		
		treeViewer.setContentProvider(new ManagerContentProvider());
		treeViewer.setLabelProvider(new ManagerLabelProvider());
		treeViewer.setInput(treeList);		
		getSite().setSelectionProvider(treeViewer);
		
		createPopupMenu();
//		getViewSite().getActionBars().getStatusLineManager().setMessage("Done"); //$NON-NLS-1$
		
		init();
		
		// db에 erd가 추가되었을 경우 
		PlatformUI.getPreferenceStore().addPropertyChangeListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				if (event.getProperty() ==  PublicTadpoleDefine.SAVE_FILE) {
					addResource(Integer.parseInt( event.getNewValue().toString().split(":")[0] )); //$NON-NLS-1$
				}
			}
		});
	}
	
	/**
	 * 트리 데이터 초기화
	 */
	public void init() {
		treeList.clear();
		
		try {
			List<String> groupNames = TadpoleSystem_UserDBQuery.getUserGroup(SessionManager.getGroupSeqs());
			for (String groupName : groupNames) {
				ManagerListDTO parent = new ManagerListDTO(groupName);
				treeList.add(parent);
			}

			List<UserDBDAO> userDBS = TadpoleSystem_UserDBQuery.getUserDB();
			for (UserDBDAO userDBDAO : userDBS) {
				addUserDB(userDBDAO, false);
			}
			
		} catch (Exception e) {
			logger.error("initialize Managerview", e);
			
			Status errStatus = new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage(), e); //$NON-NLS-1$
			ExceptionDetailsErrorDialog.openError(getSite().getShell(), "Error", Messages.ManagerViewer_4, errStatus); //$NON-NLS-1$
		}

		treeViewer.refresh();
		treeViewer.expandToLevel(2);
	}

	/**
	 * popup 화면을 오픈합니다.
	 */
	private void createPopupMenu() {
		MenuManager menuMgr = new MenuManager();
		menuMgr.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		
		Menu popupMenu = menuMgr.createContextMenu(treeViewer.getTree());
		treeViewer.getTree().setMenu(popupMenu);
		getSite().registerContextMenu(menuMgr, treeViewer);
	}

	@Override
	public void setFocus() {
	}
	
	/**
	 * 모든 트리 목록을 리턴
	 * 
	 * @return
	 */
	public List<ManagerListDTO> getAllTreeList() {
		return treeList;
	}
	
	/**
	 * 트리에 추가될수 있는것인지 검증
	 * 
	 * @param dbType
	 * @param userDB
	 */
	public boolean isAdd(DBDefine dbType, UserDBDAO userDB) {
		for(ManagerListDTO dto: treeList) {
			if(dto.getName().equals(dbType.getDBToString())) {
				if(dto.getName().equals( userDB.getDisplay_name() )) return false;
				
				for (UserDBDAO alreaduserDB : dto.getManagerList()) {
					if( alreaduserDB.getUrl().equals( userDB.getUrl() )) return false;
				}
			}
		}
	 	
		return true;
	}
	
	/**
	 * tree에 새로운 항목 추가
	 * 
	 * @param userDB
	 * @param defaultOpen default editor open
	 */
	public void addUserDB(UserDBDAO userDB, boolean defaultOpen) {
		for(ManagerListDTO dto: treeList) {
			if(dto.getName().equals(userDB.getGroup_name())) {
				dto.addLogin(userDB);
				
				if(defaultOpen) {
					selectAndOpenView(userDB);
					treeViewer.expandToLevel(userDB, 2);
				}
				return;
			}	// end if(dto.getname()....		
		}	// end for
		
		// 신규 그룹이면...
		ManagerListDTO managerDto = new ManagerListDTO(userDB.getGroup_name());
		managerDto.addLogin(userDB);
		treeList.add(managerDto);	
		
		if(defaultOpen) {
			selectAndOpenView(userDB);
			treeViewer.expandToLevel(userDB, 2);
		}
	}
	
	/**
	 * tree에 user resource 항목을 추가합니다.
	 * 
	 * @param userDB
	 */ 
	public void addUserResouceData(UserDBDAO userDB) {
		if(userDB.getListUserDBErd() == null) {
			// user_resource_data 목록을 추가해 줍니다.
			try {
				List<UserDBResourceDAO> listUserDBResources = TadpoleSystem_UserDBResource.userDbErdTree(userDB);
				if(null != listUserDBResources) {
					for (UserDBResourceDAO userDBResourceDAO : listUserDBResources) {
						userDBResourceDAO.setParent(userDB);
					}
					userDB.setListUserDBErd(listUserDBResources);
					treeViewer.expandToLevel(userDB, 3);
				}
				
			} catch (Exception e) {
				logger.error("user_db_erd list", e); //$NON-NLS-1$
				
				Status errStatus = new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage(), e); //$NON-NLS-1$
				ExceptionDetailsErrorDialog.openError(getSite().getShell(), "Error", Messages.ManagerViewer_6, errStatus); //$NON-NLS-1$
			}
		}
	}
	
	/**
	 * erd가 추가되었을때
	 * 
	 * @param userDBErd
	 */
	public void addResource(int dbSeq) {
		for(ManagerListDTO dto: treeList) {
			
			for(UserDBDAO userDB : dto.getManagerList()) {
				if(userDB.getSeq() == dbSeq) {
					userDB.getListUserDBErd().clear();
					try {
						List<UserDBResourceDAO> listUserDBErd = TadpoleSystem_UserDBResource.userDbErdTree(userDB);
						if(null != listUserDBErd) {
							for (UserDBResourceDAO userDBResouceDAO : listUserDBErd) {
								userDBResouceDAO.setParent(userDB);
							}
							userDB.setListUserDBErd(listUserDBErd);
						}
					} catch (Exception e) {
						logger.error("erd list", e); //$NON-NLS-1$
						
						Status errStatus = new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage(), e); //$NON-NLS-1$
						ExceptionDetailsErrorDialog.openError(getSite().getShell(), "Error", Messages.ManagerViewer_8, errStatus); //$NON-NLS-1$
					}
					
					treeViewer.refresh(userDB);					
					treeViewer.expandToLevel(userDB, 3);
					
					break;
				}	// if(userDB.getSeq() == dbSeq) {
				
			}	// for(UserDBDAO
				
		}
	}
	
	public void deleteErd(UserDBResourceDAO userDBResource) {
		UserDBDAO userDB = userDBResource.getParent();
		IEditorReference iEditorReference = null;
		
		// 열린화면 검색
		if(userDBResource.getResource_types().equals(PublicTadpoleDefine.RESOURCE_TYPE.SQL.toString())) {
			iEditorReference = EditorUtils.findSQLEditor(userDBResource);
		} else if(userDBResource.getResource_types().equals(PublicTadpoleDefine.RESOURCE_TYPE.ERD.toString())) {
			iEditorReference = EditorUtils.findERDEditor(userDBResource);
		}
		
		// 열린 화면 닫기
		if(iEditorReference != null) {
			PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeEditor(iEditorReference.getEditor(false), true);		
		}
		
		// 삭제
		userDBResource.getParent().getListUserDBErd().remove(userDBResource);
		treeViewer.refresh(userDB);
	}
	
//	/**
//	 * 
//	 * tree list를 삭제합니다
//	 * 
//	 * @param dbType
//	 * @param userDB
//	 */
//	public void removeTreeList(String dbType, UserDBDAO userDB) {
//		for(ManagerListDTO dto: treeList) {
//			if(dto.getName().equals(dbType)) {
//				dto.removeDB(userDB);
//				
//				treeViewer.refresh();
//				
//				// 0번째 항목이 선택되도록
//				treeViewer.getTree().select(treeViewer.getTree().getItem(0));
//				
//				return;
//			}			
//		}
//	}
	
	/**
	 * 트리를 갱신하고 쿼리 창을 엽니다.
	 * 
	 * @param dto
	 */
	public void selectAndOpenView(UserDBDAO dto) {
		treeViewer.refresh();
		treeViewer.setSelection(new StructuredSelection(dto), true);
		
		// mongodb 일경우 열지 않는다.
		if(DBDefine.getDBDefine(dto.getDbms_types()) != DBDefine.MONGODB_DEFAULT) {
			MainEditorInput mei = new MainEditorInput(dto);		
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			try {
				page.openEditor(mei, MainEditor.ID);
			} catch (PartInitException e) {
				logger.error("main editor open", e); //$NON-NLS-1$
				
				Status errStatus = new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage(), e); //$NON-NLS-1$
				ExceptionDetailsErrorDialog.openError(getSite().getShell(), "Error", Messages.ManagerViewer_10, errStatus); //$NON-NLS-1$
			}
		}
	}
}

