/*
 * Copyright (C) 2003-2010 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.cs.ext.impl;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.exoplatform.calendar.service.CalendarEvent;
import org.exoplatform.calendar.service.CalendarService;
import org.exoplatform.calendar.service.impl.CalendarEventListener;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.common.ExoSocialException;
import org.exoplatform.social.core.BaseActivityProcessorPlugin;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.activity.model.ExoSocialActivityImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Created by The eXo Platform SAS
 * Author : eXoPlatform
 *          exo@exoplatform.com
 * Jul 30, 2010  
 */
public class CalendarSpaceActivityPublisher extends CalendarEventListener {
  private final static Log   LOG                   = ExoLogger.getLogger(CalendarSpaceActivityPublisher.class);

  public static final String CALENDAR_APP_ID       = "cs-calendar:spaces";

  public static final String EVENT_ADDED           = "EventAdded".intern();

  public static final String EVENT_UPDATED         = "EventUpdated".intern();

  public static final String EVENT_ID_KEY          = "EventID".intern();

  public static final String CALENDAR_ID_KEY       = "CalendarID".intern();

  public static final String TASK_ADDED            = "TaskAdded".intern();

  public static final String TASK_UPDATED          = "TaskUpdated".intern();

  public static final String EVENT_TYPE_KEY        = "EventType".intern();

  public static final String EVENT_SUMMARY_KEY     = "EventSummary".intern();

  public static final String EVENT_TITLE_KEY       = "EventTitle".intern();

  public static final String EVENT_DESCRIPTION_KEY = "EventDescription".intern();

  public static final String EVENT_LOCALE_KEY      = "EventLocale".intern();

  public static final String EVENT_STARTTIME_KEY   = "EventStartTime".intern();

  public static final String EVENT_ENDTIME_KEY     = "EventEndTime".intern();

  public static final String EVENT_LINK_KEY        = "EventLink";

  public static final String INVITATION_DETAIL     = "/invitation/detail/";



  private static final String[] SUMMARY_UPDATED = {"summary_updated", "Summary has been updated to: $value."};
  private static final String[] DESCRIPTION_UPDATED = {"description_updated", "Description has been updated to: $value."};
  private static final String[] FROM_UPDATED = {"fromDateTime_updated", "Start date has been updated to: $value."};
  private static final String[] TO_UPDATED = {"toDateTime_updated", "End date has been updated to: $value."};
  private static final String[] LOCALTE_UPDATED = {"location_updated", "Location has been updated to: $value."};
  private static final String[] ALLDAY_UPDATED = {"allDay_updated", "Event is now an all day event."};
  private static final String[] REPEAT_UPDATED = {"repeatType_updated", "Event will be repeated each: $value."};
  private static final String[] ATTACH_UPDATED = {"attachment_updated", "Attachment(s) has been added to the event."};
  private static final String[] CATEGORY_UPDATED = {"eventCategoryName_updated", "Event's category is now: $value."};
  private static final String[] CALENDAR_UPDATED = {"calendarId_updated", "Event's calendar is now: $value."};
  private static final String[] PRIORITY_UPDATED = {"priority_updated", "Priority is now: $value."};


  private static final String[] NAME_UPDATED = {"name_updated", "Name has been updated to: $value."};
  private static final String[] NOTE_UPDATED = {"note_updated", "Note has been updated to: $value."};
  private static final String[] TASK_CATEGORY_UPDATED = {"taskCategoryName_updated", "Task's category is now: $value."};
  private static final String[] TASK_CALENDAR_UPDATED = {"task_CalendarId_updated", "Task's calendar is now: $value."};
  private static final String[] TASK_NEED_ACTION = {CalendarEvent.NEEDS_ACTION, "Task needs action."};
  private static final String[] TASK_IN_PROCESS_ACTION = {CalendarEvent.IN_PROCESS, "Task is in process."};
  private static final String[] TASK_COMPLETED_ACTION = {CalendarEvent.COMPLETED, "Task has been completed."};
  private static final String[] TASK_CANCELLED_ACTION = {CalendarEvent.CANCELLED, "Task has been cancelled."};

  private static final SimpleDateFormat dformat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
  private CalendarService calService_ ;

  /**
   * Make url for the event of the calendar application. 
   * Format of the url is: 
   * <ul>
   *    <li>/[portal]/[space]/[calendar]/[username]/invitation/detail/[event id]/[calendar type]</li>
   * </ul>
   * The format is used to utilize the invitation email feature implemented before.
   * <br>
   * <strong>[NOTE]</strong>
   * Keep in mind that this function calls {@link PortalRequestContext} which is in webui layer while this function is usually invoked in the service layer. Need to be improved in the future for ensuring the system design convention.
   * 
   * @param event have to be not null
   * @return empty string if the process is failed.
   */
  private String makeEventLink(CalendarEvent event) {
    StringBuffer sb = new StringBuffer("");    
    PortalRequestContext requestContext = Util.getPortalRequestContext();
    sb.append(requestContext.getPortalURI())
    .append(requestContext.getNodePath())
    .append(INVITATION_DETAIL)
    .append(ConversationState.getCurrent().getIdentity().getUserId())
    .append("/").append(event.getId())
    .append("/").append(event.getCalType());    
    return sb.toString();
  }

  private Map<String, String> makeActivityParams(CalendarEvent event, String calendarId, String eventType) {
    Map<String, String> params = new HashMap<String, String>();
    params.put(EVENT_TYPE_KEY, eventType);
    params.put(EVENT_ID_KEY, event.getId());
    params.put(CALENDAR_ID_KEY, calendarId);
    params.put(EVENT_SUMMARY_KEY, event.getSummary());
    params.put(EVENT_LOCALE_KEY, event.getLocation() != null ? event.getLocation() : "");
    params.put(EVENT_DESCRIPTION_KEY, event.getDescription() != null ? event.getDescription() : "");
    params.put(EVENT_STARTTIME_KEY, String.valueOf(event.getFromDateTime().getTime()));
    params.put(EVENT_ENDTIME_KEY, String.valueOf(event.getToDateTime().getTime()));
    params.put(EVENT_LINK_KEY, makeEventLink(event));
    return params;
  }

  private void publishActivity(CalendarEvent event, String calendarId, String eventType) {
    try {
      Class.forName("org.exoplatform.social.core.space.spi.SpaceService");
    } catch (ClassNotFoundException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("eXo Social components not found!", e);
      }
      return;
    }
    if (calendarId == null || calendarId.indexOf(CalendarDataInitialize.SPACE_CALENDAR_ID_SUFFIX) < 0) {
      return;
    }
    try{
      IdentityManager identityM = (IdentityManager) PortalContainer.getInstance().getComponentInstanceOfType(IdentityManager.class);
      ActivityManager activityM = (ActivityManager) PortalContainer.getInstance().getComponentInstanceOfType(ActivityManager.class);
      SpaceService spaceService = (SpaceService) PortalContainer.getInstance().getComponentInstanceOfType(SpaceService.class);
      String spacePrettyName = calendarId.split(CalendarDataInitialize.SPACE_CALENDAR_ID_SUFFIX)[0];
      Space space = spaceService.getSpaceByPrettyName(spacePrettyName);
      if (space != null) {
        String userId = ConversationState.getCurrent().getIdentity().getUserId();
        Identity spaceIdentity = identityM.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);
        Identity userIdentity = identityM.getOrCreateIdentity(OrganizationIdentityProvider.NAME, userId, false);
        ExoSocialActivity activity = new ExoSocialActivityImpl();
        activity.setUserId(userIdentity.getId());
        activity.setTitle(event.getSummary());
//        activity.setBody(event.getDescription());
        activity.setType("CALENDAR_ACTIVITY");
//        activity.setTemplateParams(makeActivityParams(event, calendarId, eventType));
        activityM.saveActivityNoReturn(spaceIdentity, activity);
        event.setActivityId(activity.getId());
      }
    }catch(ExoSocialException e){ //getSpaceByPrettyName
      if (LOG.isErrorEnabled())
        LOG.error("Can not record Activity for space when event added ", e);
    }
  }

  private void commnetToActivity(CalendarEvent event, String calendarId, String eventType) {

  }

  private void updateToActivity(CalendarEvent event, String calendarId, String eventType, Map<String, String[]> messagesParams){
    try {
      Class.forName("org.exoplatform.social.core.space.spi.SpaceService");
    } catch (ClassNotFoundException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("eXo Social components not found!", e);
      }
      return;
    }
    if (calendarId == null || calendarId.indexOf(CalendarDataInitialize.SPACE_CALENDAR_ID_SUFFIX) < 0) {
      return;
    }
    try{
      IdentityManager identityM = (IdentityManager) PortalContainer.getInstance().getComponentInstanceOfType(IdentityManager.class);
      ActivityManager activityM = (ActivityManager) PortalContainer.getInstance().getComponentInstanceOfType(ActivityManager.class);
      SpaceService spaceService = (SpaceService) PortalContainer.getInstance().getComponentInstanceOfType(SpaceService.class);
      String spacePrettyName = calendarId.split(CalendarDataInitialize.SPACE_CALENDAR_ID_SUFFIX)[0];
      Space space = spaceService.getSpaceByPrettyName(spacePrettyName);
      if (space != null && event.getActivityId() != null) {
        String userId = ConversationState.getCurrent().getIdentity().getUserId();
        Identity spaceIdentity = identityM.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);
        Identity userIdentity = identityM.getOrCreateIdentity(OrganizationIdentityProvider.NAME, userId, false);
        ExoSocialActivity activity = activityM.getActivity(event.getActivityId()) ;
        if(activity == null){ activity = new ExoSocialActivityImpl() ;
        activity.setUserId(userIdentity.getId());
        activity.setTitle(event.getSummary());
        activity.setBody(event.getDescription());
        activity.setType("CALENDAR_ACTIVITY");
        activity.setTemplateParams(makeActivityParams(event, calendarId, eventType));
        activityM.saveActivityNoReturn(spaceIdentity, activity);
        } else {
//          activity.setTitle(event.getSummary());
//          activity.setBody(event.getDescription());
//          activity.setTemplateParams(makeActivityParams(event, calendarId, eventType));
//          activityM.updateActivity(activity);
          for(String key : messagesParams.keySet()) {
            ExoSocialActivity newComment = new ExoSocialActivityImpl();
//            newComment.setUserId(userIdentity.getId());
//            newComment.isComment(true);
            newComment.setType("cs-calendar:spaces");
            newComment.setTitleId("summary_updated");
            Map<String, String> data = new LinkedHashMap<String, String>(); 
            data.put("SUMMARY_PARAM", messagesParams.get(key)[1]);
            data.put(BaseActivityProcessorPlugin.TEMPLATE_PARAM_TO_PROCESS, "SUMMARY_PARAM");
            newComment.setTemplateParams(data);
            
//            Map<String, String> templateParams = new LinkedHashMap<String, String>();
//            templateParams.put("SUMMARY_PARAM", event.getProfile().getPosition());
//            templateParams.put(BaseActivityProcessorPlugin.TEMPLATE_PARAM_TO_PROCESS, "SUMMARY_PARAM");
//            comment.setTemplateParams(templateParams);
            
            newComment.setTitle(messagesParams.get(key)[0]);
            activityM.saveComment(activity, newComment);
          } 
        }
      }

    } catch (ExoSocialException e){  
      if (LOG.isErrorEnabled())
        LOG.error("Can not update Activity for space when event modified ", e);
    }
  }

  private void deleteActivity(CalendarEvent event, String calendarId, String eventType){
    try {
      Class.forName("org.exoplatform.social.core.space.spi.SpaceService");
    } catch (ClassNotFoundException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("eXo Social components not found!", e);
      }
      return;
    }
    if (calendarId == null || calendarId.indexOf(CalendarDataInitialize.SPACE_CALENDAR_ID_SUFFIX) < 0) {
      return;
    }
    try{
      ActivityManager activityM = (ActivityManager) PortalContainer.getInstance().getComponentInstanceOfType(ActivityManager.class);
      SpaceService spaceService = (SpaceService) PortalContainer.getInstance().getComponentInstanceOfType(SpaceService.class);
      String spacePrettyName = calendarId.split(CalendarDataInitialize.SPACE_CALENDAR_ID_SUFFIX)[0];
      Space space = spaceService.getSpaceByPrettyName(spacePrettyName);
      if (space != null && event.getActivityId() != null) {
        activityM.deleteActivity(event.getActivityId());
      }
    } catch (ExoSocialException e){ //getSpaceByPrettyName
      if (LOG.isErrorEnabled())
        LOG.error("Can not delete Activity for space when event deleted ", e);
    }
  }
  private Map<String, String[]> buildParams(CalendarEvent oldEvent, CalendarEvent newEvent){
    Map<String, String[]> messagesParams = new HashMap<String, String[]>();
    if(CalendarEvent.TYPE_EVENT.equals(newEvent.getEventType())) {
      if(!oldEvent.getSummary().equals(newEvent.getSummary())) {
        messagesParams.put(SUMMARY_UPDATED[0],new String[]{SUMMARY_UPDATED[1].replace("$value", newEvent.getSummary()),newEvent.getSummary()}) ;
      }
    }/*
      if(oldEvent.getDescription() != null && !oldEvent.getDescription().equals(newEvent.getDescription())) {
        messagesParams.put(DESCRIPTION_UPDATED[0],new String[]{DESCRIPTION_UPDATED[1].replace("$value", newEvent.getDescription()),newEvent.getDescription()}) ;
      }
      if(!isAllDayEvent(oldEvent) || !isAllDayEvent(oldEvent)) {
        if(!oldEvent.getAttachment().equals(newEvent.getAttachment())) {
          messagesParams.put(ALLDAY_UPDATED[0],new String[]{ALLDAY_UPDATED[1], ALLDAY_UPDATED[1]}) ;
        }
      } else {
        if(!oldEvent.getFromDateTime().equals(newEvent.getFromDateTime())) {
          messagesParams.put(FROM_UPDATED[0],new String[]{FROM_UPDATED[1].replace("$value", dformat.format(newEvent.getFromDateTime())),dformat.format(newEvent.getFromDateTime())}) ;
        }
        if(!oldEvent.getToDateTime().equals(newEvent.getToDateTime())) {
          messagesParams.put(TO_UPDATED[0],new String[]{TO_UPDATED[1].replace("$value", dformat.format(newEvent.getToDateTime())),dformat.format(newEvent.getToDateTime())}) ;
        }
      }
      if(oldEvent.getLocation()!= null && !oldEvent.getLocation().equals(newEvent.getLocation())) {
        messagesParams.put(LOCALTE_UPDATED[0],new String[]{LOCALTE_UPDATED[1].replace("$value", newEvent.getLocation()),newEvent.getLocation()}) ;
      }
      if(oldEvent.getRepeatType()!= null && !oldEvent.getRepeatType().equals(newEvent.getRepeatType())) {
        messagesParams.put(REPEAT_UPDATED[0],new String[]{REPEAT_UPDATED[1].replace("$value", newEvent.getRepeatType()),newEvent.getRepeatType()}) ;
      }
      if(oldEvent.getAttachment() != null &&  !oldEvent.getAttachment().equals(newEvent.getAttachment())) {
        messagesParams.put(ATTACH_UPDATED[0],new String[]{ATTACH_UPDATED[1], ATTACH_UPDATED[1]}) ;
      }
      if(!oldEvent.getEventCategoryName().equals(newEvent.getEventCategoryName())) {
        messagesParams.put(CATEGORY_UPDATED[0],new String[]{CATEGORY_UPDATED[1].replace("$value", newEvent.getEventCategoryName()),newEvent.getEventCategoryName()}) ;
      }
      if(!oldEvent.getCalendarId().equals(newEvent.getCalendarId())) {
        try {
          CalendarService calService = (CalendarService) PortalContainer.getInstance().getComponentInstanceOfType(CalendarService.class);
          org.exoplatform.calendar.service.Calendar cal = calService.getGroupCalendar(newEvent.getCalendarId());
          messagesParams.put(CALENDAR_UPDATED[0],new String[]{CALENDAR_UPDATED[1].replace("$value", cal.getName()),cal.getName()}) ;
        } catch (Exception e) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Calendar is not found!", e);
          }
        }
      }
      if(oldEvent.getPriority()!= null && !oldEvent.getPriority().equals(newEvent.getPriority())) {
        messagesParams.put(PRIORITY_UPDATED[0],new String[]{PRIORITY_UPDATED[1].replace("$value", newEvent.getPriority()),newEvent.getPriority()}) ;
      }
    } else {
      if(!oldEvent.getSummary().equals(newEvent.getSummary())) {
        messagesParams.put(NAME_UPDATED[0],new String[]{NAME_UPDATED[1].replace("$value", newEvent.getSummary()),newEvent.getSummary()}) ;
      }
      if(!oldEvent.getDescription().equals(newEvent.getDescription())) {
        messagesParams.put(NOTE_UPDATED[0],new String[]{NOTE_UPDATED[1].replace("$value", newEvent.getDescription()),newEvent.getDescription()}) ;
      }
      if(!oldEvent.getFromDateTime().equals(newEvent.getFromDateTime())) {
        messagesParams.put(FROM_UPDATED[0],new String[]{FROM_UPDATED[1].replace("$value", dformat.format(newEvent.getFromDateTime())),dformat.format(newEvent.getFromDateTime())}) ;
      }
      if(!oldEvent.getToDateTime().equals(newEvent.getToDateTime())) {
        messagesParams.put(TO_UPDATED[0],new String[]{TO_UPDATED[1].replace("$value", dformat.format(newEvent.getToDateTime())),dformat.format(newEvent.getToDateTime())}) ;
      }
      if(oldEvent.getAttachment() != null && !oldEvent.getAttachment().equals(newEvent.getAttachment())) {
        messagesParams.put(ATTACH_UPDATED[0],new String[]{ATTACH_UPDATED[1], ATTACH_UPDATED[1]}) ;
      }
      if(!oldEvent.getEventCategoryName().equals(newEvent.getEventCategoryName())) {
        messagesParams.put(TASK_CATEGORY_UPDATED[0],new String[]{TASK_CATEGORY_UPDATED[1].replace("$value", newEvent.getEventCategoryName()),newEvent.getEventCategoryName()}) ;
      }
      if(!oldEvent.getCalendarId().equals(newEvent.getCalendarId())) {
        try {
          CalendarService calService = (CalendarService) PortalContainer.getInstance().getComponentInstanceOfType(CalendarService.class);
          org.exoplatform.calendar.service.Calendar cal = calService.getGroupCalendar(newEvent.getCalendarId());
          messagesParams.put(TASK_CALENDAR_UPDATED[0],new String[]{TASK_CALENDAR_UPDATED[1].replace("$value", cal.getName()),cal.getName()}) ;
        } catch (Exception e) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Calendar is not found!", e);
          }
        }
      }
      if(!oldEvent.getStatus().equals(newEvent.getStatus())) {
        if(CalendarEvent.NEEDS_ACTION.equals(newEvent.getStatus())) {
          messagesParams.put(TASK_NEED_ACTION[0],new String[]{TASK_NEED_ACTION[1].replace("$value", newEvent.getStatus()),newEvent.getStatus()}) ;
        } else if(CalendarEvent.IN_PROCESS.equals(newEvent.getStatus())) {
          messagesParams.put(TASK_IN_PROCESS_ACTION[0],new String[]{TASK_IN_PROCESS_ACTION[1].replace("$value", newEvent.getStatus()),newEvent.getStatus()}) ;
        } else if(CalendarEvent.COMPLETED.equals(newEvent.getStatus())) {
          messagesParams.put(TASK_COMPLETED_ACTION[0],new String[]{TASK_COMPLETED_ACTION[1].replace("$value", newEvent.getStatus()),newEvent.getStatus()}) ;
        } else if(CalendarEvent.CANCELLED.equals(newEvent.getStatus())) {
          messagesParams.put(TASK_CANCELLED_ACTION[0],new String[]{TASK_CANCELLED_ACTION[1].replace("$value", newEvent.getStatus()),newEvent.getStatus()}) ;
        }
      }
    }*/
    
    return messagesParams;
  }

  private boolean isAllDayEvent(CalendarEvent eventCalendar) {
    Calendar calendar = GregorianCalendar.getInstance() ;
    calendar.setLenient(false);
    Calendar cal1 = calendar ;
    Calendar cal2 = calendar ;
    cal1.setTime(eventCalendar.getFromDateTime()) ;
    cal2.setTime(eventCalendar.getToDateTime()) ;
    return (cal1.get(Calendar.HOUR_OF_DAY) == 0  && 
        cal1.get(Calendar.MINUTE) == 0 &&
        cal2.get(Calendar.HOUR_OF_DAY) == cal2.getActualMaximum(Calendar.HOUR_OF_DAY)&& 
        cal2.get(Calendar.MINUTE) == cal2.getActualMaximum(Calendar.MINUTE) );
  }

  public void savePublicEvent(CalendarEvent event, String calendarId) {
    String eventType = event.getEventType().equalsIgnoreCase(CalendarEvent.TYPE_EVENT) ? EVENT_ADDED : TASK_ADDED;
    publishActivity(event, calendarId, eventType);
  }

  public void updatePublicEvent(CalendarEvent event, String calendarId) {}


  public void updatePublicEvent(CalendarEvent oldEvent, CalendarEvent newEvent, String calendarId) {
    String eventType = newEvent.getEventType().equalsIgnoreCase(CalendarEvent.TYPE_EVENT) ? EVENT_ADDED : TASK_ADDED;
    Map<String, String[]> messagesParams = buildParams(oldEvent, newEvent);
    updateToActivity(newEvent, calendarId, eventType, messagesParams);
  }

  public void deletePublicEvent(CalendarEvent event, String calendarId) {
    String eventType = event.getEventType().equalsIgnoreCase(CalendarEvent.TYPE_EVENT) ? EVENT_ADDED : TASK_ADDED;
    deleteActivity(event, calendarId, eventType) ;
  }


}
