/*
 * ContactEdit.java
 *
 * Created on 26.05.2008, 10:04
 *
 * Copyright (c) 2006-2008, Daniel Apatin (ad), http://apatin.net.ru
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * You can also redistribute and/or modify this program under the
 * terms of the Psi License, specified in the accompanied COPYING
 * file, as published by the Psi Project; either dated January 1st,
 * 2005, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package Client;

import xmpp.Jid;

//#ifndef WMUC
import Conference.MucContact;
//#endif
//#ifdef PRIVACY
//# import PrivacyLists.QuickPrivacy;
//#endif
import VCard.VCard;
import java.util.*;
import locale.SR;
import ui.controls.form.LinkString;
import ui.controls.form.SimpleString;
import ui.controls.form.CheckBox;
import ui.controls.form.DefForm;
import ui.controls.form.DropChoiceBox;
import ui.controls.form.TextInput;
import xmpp.JidUtils;

/**
 *
 * @author Evg_S
 */
public final class ContactEdit
        extends DefForm {

    private LinkString vCardReq;
    private TextInput tJid;
    private TextInput tNick;
    private TextInput tGroup;
    private DropChoiceBox tGrpList;
    private DropChoiceBox tTranspList;
    private CheckBox tAskSubscrCheckBox;
    int ngroups;
    int newGroupPos = 0;
    boolean newContact = true;
    private boolean newGroup;

    public ContactEdit(Contact c) {
        super(SR.MS_ADD_CONTACT);
        cf = Config.getInstance();

        tJid = new TextInput(SR.MS_USER_JID, null, null);

        tNick = new TextInput(SR.MS_NAME, null, null);

        tGroup = new TextInput(SR.MS_NEWGROUP, (c == null) ? "" : c.group.name, null);

        tTranspList = new DropChoiceBox(SR.MS_TRANSPORT);
        // Transport droplist
        tTranspList.add(sd.account.JID.getServer());
        for (Enumeration e = sd.roster.hContacts.elements(); e.hasMoreElements();) {
            Contact ct = (Contact) e.nextElement();
            Jid transpJid = ct.jid;
            if (JidUtils.isTransport(transpJid)) {
                tTranspList.add(transpJid.getBare());
            }
        }
        tTranspList.add(SR.MS_OTHER);
        tTranspList.setSelectedIndex(tTranspList.size() - 1);

        tAskSubscrCheckBox = new CheckBox(SR.MS_ASK_SUBSCRIPTION, false);

        try {
            String jid;
//#ifndef WMUC
            if (c instanceof MucContact) {
                jid = ((MucContact) c).realJid.getBare();
            } else {
//#endif
                jid = c.jid.getBare();
//#ifndef WMUC
            }
//#endif
            // edit contact
            tJid.setValue(jid);
            tNick.setValue(c.nick);
//#ifndef WMUC
            if (c instanceof MucContact) {
                c = null;
                throw new Exception();
            }
//#endif
            if (c.getGroupType() != Groups.TYPE_NOT_IN_LIST && c.getGroupType() != Groups.TYPE_SEARCH_RESULT) {
                // edit contact
                mainbar.setElementAt(jid, 0);
                newContact = false;
            } else {
                c = null; // adding not-in-list
            }
        } catch (Exception e) {
            c = null;
        } // if MucContact does not contains realJid

        int sel = -1;
        ngroups = 0;
        String grpName = "";
        if (c != null) {
            grpName = c.group.name;
        }

        Vector groups = sd.roster.groups.getRosterGroupNames();
        if (groups != null) {
            tGrpList = new DropChoiceBox(SR.MS_GROUP);
            ngroups = groups.size();
            for (int i = 0; i < ngroups; i++) {
                String gn = (String) groups.elementAt(i);
                tGrpList.add(gn);

                if (gn.equals(grpName)) {
                    sel = i;
                }
            }
        }
        if (sel < 0) {
            sel = 0;
        }

        if (c == null) {
            itemsList.addElement(tJid);

            itemsList.addElement(tTranspList);
        }
        itemsList.addElement(tNick);

        tGrpList.add(SR.MS_NEWGROUP);
        tGrpList.setSelectedIndex(sel);
        itemsList.addElement(tGrpList);

        newGroupPos = itemsList.indexOf(tGrpList) + 1;      

        if (newContact) {
            itemsList.addElement(new SimpleString(SR.MS_SUBSCRIPTION, true));
            itemsList.addElement(tAskSubscrCheckBox);

            vCardReq = new LinkString(SR.MS_VCARD) {

                public void doAction() {
                    requestVCard();
                }
            };
            itemsList.addElement(vCardReq);
        }

        moveCursorTo(getNextSelectableRef(-1));

    }

    private void requestVCard() {
        String jid = tJid.getValue();
        if (jid.length() > 0) {
            VCard.request(jid, jid);
        }
    }

    public void cmdOk() {
        String jid = tJid.getValue().trim().toLowerCase();
        if (jid != null) {
            String name = tNick.getValue();
            String group = group(tGrpList.getSelectedIndex());

            if (tGrpList.getSelectedIndex() == tGrpList.size() - 1) {
                group = tGroup.getValue();
            }

            boolean ask = tAskSubscrCheckBox.getValue();
//#ifdef PRIVACY                        
//#             if (!sd.account.isGoogle) {
//#                                  if (QuickPrivacy.groupsList == null) {
//#                                      QuickPrivacy.groupsList = new Vector();
//#                                  }
//#                                  if (!QuickPrivacy.groupsList.contains(group)) {
//#                                      QuickPrivacy.groupsList.addElement(group); 
//#                                      new QuickPrivacy().updateQuickPrivacyList();
//#                                  }
//#             }
//#endif                                                        
            

            if (group.equals(SR.MS_GENERAL)) {
                group = "";
            }               

            int at = jid.indexOf('@');
            if (at < 0 && tTranspList.getSelectedIndex() != tTranspList.size() - 1) {
                StringBuffer jidBuf = new StringBuffer(jid);
                at = jid.length();
                jidBuf.setLength(at);
                jidBuf.append('@').append((String) tTranspList.items.elementAt(tTranspList.getSelectedIndex()));
                jid = jidBuf.toString();
            }
            Jid newJid = new Jid(jid);
            if (!newJid.equals(StaticData.getInstance().roster.selfContact().jid, false)) {
                Contact c = sd.roster.getContact(newJid.getBare(), true);
                Group grp = sd.roster.groups.getGroup(group);
                    if (grp == null) {
                        grp = sd.roster.groups.addGroup(group, Groups.TYPE_COMMON);
                    }
		c.group = grp;
                c.nick = name;
                sd.roster.storeContact(c, ask);
            }
            destroyView();
        }
    }

    protected void beginPaint() {
        if (tGrpList != null) {
            if (tGrpList.toString().equals(SR.MS_NEWGROUP)) {
                if (!newGroup) {
                    itemsList.insertElementAt(tGroup, newGroupPos);
                    newGroup = true;
                }
            } else {
                if (newGroup) {
                    itemsList.removeElement(tGroup);
                    newGroup = false;
                }
            }
        }
    }

    private String group(int index) {
        if (index == 0) {
            return "";
        }
        if (index == tGrpList.size() - 1) {
            return "";
        }
        return (String) tGrpList.items.elementAt(index);
    }
}
