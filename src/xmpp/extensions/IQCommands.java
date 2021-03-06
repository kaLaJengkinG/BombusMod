/*
 * IQCommands.java
 *
 * Created on 4.07.2008, 14:48
 *
 * Copyright (c) 2006-2008, Daniel Apatin (ad), http://apatin.net.ru
 * Copyright (c) 2009, Vladimir Kichigin (voffk), http://voffk.org.ru
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
 *
 */

package xmpp.extensions;

import Client.Contact;
import xmpp.Jid;
import Client.StaticData;
//#ifndef WMUC
import Conference.ConferenceGroup;
import Conference.MucContact;
//#endif
import ServiceDiscovery.DiscoForm;
import ServiceDiscovery.ServiceDiscovery;
import com.alsutton.jabber.JabberBlockListener;
import com.alsutton.jabber.JabberDataBlock;
import com.alsutton.jabber.datablocks.Iq;
import java.util.Enumeration;
import ui.Time;

/**
 *
 * @author ad
 */
public class IQCommands implements JabberBlockListener {
    
    /** Singleton */
    private static IQCommands instance;
    
    public static IQCommands getInstance() {
        if (instance==null) instance=new IQCommands();
        return instance;
    }
   
    StaticData sd = StaticData.getInstance();
    
    /** Creates a new instance of PepListener */
    private IQCommands() { }
    
    public void addBlockListener() {
        sd.theStream.addBlockListener(instance);
    }
        
    public int blockArrived(JabberDataBlock data) {
        if (!(data instanceof Iq)) return BLOCK_REJECTED;
        String type=data.getTypeAttribute();
        String from=data.getAttribute("from");
        
        if (from!=null) {
            if (!new Jid(from).equals(sd.roster.selfContact().jid, false))
                return BLOCK_REJECTED;
        } else return BLOCK_REJECTED;
        
        //System.out.println(">>> "+data.toString());
        
        if (type.equals("get")) {
            JabberDataBlock query=data.getChildBlock("query");
            if (query.isJabberNameSpace(ServiceDiscovery.NS_INFO)) {
                Iq reply=new Iq(data.getAttribute("from"), Iq.TYPE_RESULT, data.getAttribute("id"));
                JabberDataBlock replyChild=reply.addChildNs("query", ServiceDiscovery.NS_INFO);
                if (query.getAttribute("node")!=null)
                    if (!query.getAttribute("node").equals(ServiceDiscovery.NS_CMDS)) {
                        replyChild.setAttribute("node", query.getAttribute("node"));
                        JabberDataBlock identity=replyChild.addChild("identity", "");
                            identity.setAttribute("category", "automation");
                            identity.setTypeAttribute("command-node");
                        JabberDataBlock feature1=replyChild.addChild("feature", "");
                            feature1.setAttribute("var", ServiceDiscovery.NS_CMDS);
                        JabberDataBlock feature2=replyChild.addChild("feature", "");
                            feature2.setAttribute("var", DiscoForm.NS_XDATA);
                    }
                sd.theStream.send(reply);
                return BLOCK_PROCESSED;
                
            } else if (query.isJabberNameSpace(ServiceDiscovery.NS_ITEMS)) {
                if (!query.getAttribute("node").equals(ServiceDiscovery.NS_CMDS)) {
                    Iq reply=new Iq(data.getAttribute("from"), Iq.TYPE_RESULT, data.getAttribute("id"));
                    JabberDataBlock replyChild=reply.addChildNs("query", ServiceDiscovery.NS_ITEMS);
                    if (query.getAttribute("node")!=null)
                        replyChild.setAttribute("node", query.getAttribute("node"));
                    sd.theStream.send(reply);
                    return BLOCK_PROCESSED;
                } else {
                    Iq reply=new Iq(data.getAttribute("from"), Iq.TYPE_RESULT, data.getAttribute("id"));
                    reply.addChild(query);

                    //http://jabber.org/protocol/rc#set-status //4.1 Change Status
                    JabberDataBlock status=query.addChild("item", "");
                    status.setAttribute("jid", sd.roster.selfContact().getJid().toString());
                    status.setAttribute("node", "set-status");
                    status.setAttribute("name", "Set Status");
//#ifndef WMUC
                    //http://jabber.org/protocol/rc#leave-groupchats //4.5 Leave Groupchats 
                    JabberDataBlock leaveChats=query.addChild("item", "");
                    leaveChats.setAttribute("jid", sd.roster.selfContact().getJid().toString());
                    leaveChats.setAttribute("node", "leave-groupchats");
                    leaveChats.setAttribute("name", "Leave Groupchats");
//#endif
                    sd.theStream.send(reply);

                    return BLOCK_PROCESSED;
                }
            }
        } else if (type.equals("set")) {
            JabberDataBlock command=data.findNamespace("command", ServiceDiscovery.NS_CMDS);
            if (command==null) return BLOCK_REJECTED;
            
            if (command.getAttribute("node").equals("set-status")) {
                if (command.getAttribute("sessionid")==null) {
                    processStatusRequest(data);
                    return BLOCK_PROCESSED;
                } else /*if (!command.getAttribute("action").equals("cancel"))*/ {
                    //parsing task
                    JabberDataBlock x=command.findNamespace("x", DiscoForm.NS_XDATA);
                    if (!x.getTypeAttribute().equals("submit"))
                        return BLOCK_REJECTED;

                    String status="";
                    //String priority="";
                    String message="";

                    for (Enumeration e=x.getChildBlocks().elements(); e.hasMoreElements(); ){
                        JabberDataBlock field=(JabberDataBlock) e.nextElement();
                        if (field.getAttribute("var").equals("status")) {
                            status=field.getChildBlockText("value");
                        } //else if (field.getAttribute("var").equals("status-priority")) {
                            //priority=field.getChildBlockText("value");
                        //}
                            else if (field.getAttribute("var").equals("status-message")) {
                            message=field.getChildBlockText("value");
                        }
                    }
                    int newStatus=0;
                    if (status.equals("chat")) newStatus=1;
                    else if (status.equals("away")) newStatus=2;
                    else if (status.equals("xa")) newStatus=3;
                    else if (status.equals("dnd")) newStatus=4;
                    else if (status.equals("invisible")) newStatus=8;
                    else if (status.equals("offline")) newStatus=5;

                    sd.roster.sendPresence(newStatus, message);

                    Iq reply=new Iq(data.getAttribute("from"), Iq.TYPE_RESULT, data.getAttribute("id"));
                    JabberDataBlock cmd=reply.addChildNs("command", ServiceDiscovery.NS_CMDS);
                    cmd.setAttribute("status", "completed");
                    cmd.setAttribute("node", "set-status");
                    cmd.setAttribute("sessionid", command.getAttribute("sessionid"));
                    sd.theStream.send(reply);
                    
                    return BLOCK_PROCESSED;
                }
            } 
//#ifndef WMUC            
            else if (command.getAttribute("node").equals("leave-groupchats")) {
                if (command.getAttribute("sessionid")==null) {
                    processGCRequest(data);
                    return BLOCK_PROCESSED;
                } else /*if (!command.getAttribute("action").equals("cancel"))*/ {
                    //parsing task
                    JabberDataBlock x=command.findNamespace("x", DiscoForm.NS_XDATA);
                    if (!x.getTypeAttribute().equals("submit"))
                        return BLOCK_REJECTED;
                    
                    for (Enumeration e=x.getChildBlocks().elements(); e.hasMoreElements(); ) {
                        JabberDataBlock field=(JabberDataBlock) e.nextElement();
                        if (field.getAttribute("var").equals("groupchats")) {
                            for (Enumeration e2=field.getChildBlocks().elements(); e2.hasMoreElements(); ){
                                JabberDataBlock value=(JabberDataBlock) e2.nextElement();
                                String roomName=value.getText();
                                
                                for (Enumeration c=sd.roster.hContacts.elements(); c.hasMoreElements(); ) {
                                    try {
                                        Contact cl=(Contact) c.nextElement();
                                        if (cl.origin!=Contact.ORIGIN_GROUPCHAT) continue;
                                        if (!((MucContact)cl).commonPresence) continue; // stop if room left manually
                                        ConferenceGroup confGroup=(ConferenceGroup)cl.group;

                                        if (!confGroup.inRoom) continue; // don't process leaved leaved

                                        if (confGroup.name.equals(roomName))
                                            sd.roster.leaveRoom(confGroup);
                                    } catch (Exception ex) {}
                                }
                            }
                        }
                    }
                    
                    Iq reply=new Iq(data.getAttribute("from"), Iq.TYPE_RESULT, data.getAttribute("id"));
                    JabberDataBlock cmd=reply.addChildNs("command", ServiceDiscovery.NS_CMDS);
                    cmd.setAttribute("status", "completed");
                    cmd.setAttribute("node", "leave-groupchats");
                    cmd.setAttribute("sessionid", command.getAttribute("sessionid"));
                    sd.theStream.send(reply);
                    
                    return BLOCK_PROCESSED;
                }
            }
//#endif            
        }
        return BLOCK_REJECTED;
    }
    
    private void processStatusRequest(JabberDataBlock data) {
        Iq reply=new Iq(data.getAttribute("from"), Iq.TYPE_RESULT, data.getAttribute("id"));

        JabberDataBlock cmd=reply.addChildNs("command", ServiceDiscovery.NS_CMDS);
        cmd.setAttribute("status", "executing");
        cmd.setAttribute("node", "set-status");
        cmd.setAttribute("sessionid", "set-status:"+Time.utcTime());

        JabberDataBlock x=cmd.addChildNs("x", DiscoForm.NS_XDATA);
        x.setTypeAttribute("form");
        x.addChild("title", "Change Status");
        x.addChild("instructions", "Choose the status and status message");

        JabberDataBlock fieldHidden=x.addChild("field", "");
        fieldHidden.setTypeAttribute("hidden");
        fieldHidden.setAttribute("var", "FORM_TYPE");
        fieldHidden.addChild("value", "http://jabber.org/protocol/rc");

        JabberDataBlock fieldStatus=x.addChild("field", "");
        fieldStatus.setTypeAttribute("list-single");
        fieldStatus.setAttribute("var", "status");
        fieldStatus.setAttribute("label", "Status");
        fieldStatus.addChild("value", "online");
        fieldStatus.addChild("required", "");

        for (int i=0; i<7; i++) {
            JabberDataBlock labelStatus=fieldStatus.addChild("option", "");
            labelStatus.setAttribute("label", statusesDesc[i]);
            labelStatus.addChild("value", statuses[i]);
        }
/*
        JabberDataBlock fieldPriority=x.addChild("field", "");
        fieldPriority.setTypeAttribute("text-single");
        fieldPriority.setAttribute("var", "status-priority");
        fieldPriority.setAttribute("label", "Priority");
        fieldPriority.addChild("value", "0");
*/
        JabberDataBlock fieldMessage=x.addChild("field", "");
        fieldMessage.setTypeAttribute("text-multi");
        fieldMessage.setAttribute("var", "status-message");
        fieldMessage.setAttribute("label", "Message");

        sd.theStream.send(reply);
        //System.out.println(">>> "+reply.toString());
    }
//#ifndef WMUC    
    private void processGCRequest(JabberDataBlock data) {
        Iq reply=new Iq(data.getAttribute("from"), Iq.TYPE_RESULT, data.getAttribute("id"));

        JabberDataBlock cmd=reply.addChildNs("command", ServiceDiscovery.NS_CMDS);
        cmd.setAttribute("status", "executing");
        cmd.setAttribute("node", "leave-groupchats");
        cmd.setAttribute("sessionid", "leave-groupchats:"+Time.utcTime());

        JabberDataBlock x=cmd.addChildNs("x", DiscoForm.NS_XDATA);
        x.setTypeAttribute("form");
        x.addChild("title", "Leave Groupchats");
        x.addChild("instructions", "Choose the groupchats you want to leave");

        JabberDataBlock fieldHidden=x.addChild("field", "");
        fieldHidden.setTypeAttribute("hidden");
        fieldHidden.setAttribute("var", "FORM_TYPE");
        fieldHidden.addChild("value", "http://jabber.org/protocol/rc");

        JabberDataBlock fieldGroupchats=x.addChild("field", "");
        fieldGroupchats.setTypeAttribute("list-multi");
        fieldGroupchats.setAttribute("var", "groupchats");
        fieldGroupchats.setAttribute("label", "Groupchats");
        fieldGroupchats.addChild("value", "online");
        fieldGroupchats.addChild("required", "");

        for (Enumeration c=sd.roster.hContacts.elements(); c.hasMoreElements(); ) {
            try {
                MucContact mc=(MucContact)c.nextElement();
                if (mc.origin==Contact.ORIGIN_GROUPCHAT && mc.status==0) {
                    JabberDataBlock labelOnline=fieldGroupchats.addChild("option", "");
                    labelOnline.setAttribute("label", mc.getJid().toString());
                    labelOnline.addChild("value", mc.getJid().toString());
                }
            } catch (Exception e) {}
        }

        sd.theStream.send(reply);
        //System.out.println(">>> "+reply.toString());
    }
//#endif    
    
    private static final String[] statuses = {"online", "chat", "away", "xa", "dnd", "invisible", "offline"};
    private static final String[] statusesDesc = {"Online", "Chat", "Away", "Extended Away", "Do Not Disturb", "Invisible", "Offline"};
}
