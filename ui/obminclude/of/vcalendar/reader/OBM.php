<?php

include_once('obminclude/of/Vcalendar.php');

//FIXME re-think read functions

class Vcalendar_Reader_OBM {

  var $document;

  var $entities;

  var $vevents = array();
  
  var $weekDays = array( 'sunday' => 'su', 'monday' => 'mo', 'tuesday' => 'tu', 'wednesday' => 'we', 'thursday' => 'th' , 'friday' => 'fr', 'saturday' => 'sa');

  var $eventSets = array();
  
  function Vcalendar_Reader_OBM($entities,$ids = array(), $startTime = NULL, $endTime = NULL) {
    $this->db = new DB_OBM;
    $this->entities = $entities;
    if(!empty($ids)) {
      $this->readSet($ids);
    } else {
      $ids = array();
    }
    if(!is_null($startTime) && !is_null($endTime)) {
      $ids = array_merge($ids,$this->readPeriod($startTime,$endTime));
    }
    $this->alerts = $this->readEventsAlerts($ids);
  }

  function readPeriod($startTime, $endTime) {
    $noRepeatEvent = run_query_calendar_no_repeat_events($startTime,$endTime,$this->entities,NULL);
    $repeatEvent = run_query_calendar_repeat_events($startTime,$endTime,$this->entities,NULL);
    $ids = array();
    while($noRepeatEvent->next_record()) {
      $ids[] = $noRepeatEvent->f('event_id');
      $this->eventSets[] = $noRepeatEvent->Record;
    }
    while($repeatEvent->next_record()) {
      $ids[] = $repeatEvent->f('event_id');
      $this->eventSets[] = $repeatEvent->Record;
    }
    return $ids;
  }
   
  function readSet($events) {
    foreach($events as $eventId) {
      $set = run_query_calendar_detail($eventId);
      $this->eventSets[] = $set->Record;
    }
  }

  function readEventsAlerts($events) {
    if(empty($events)) {
      return array();
    }
    $query = 'SELECT eventalert_duration, eventalert_user_id, eventalert_event_id FROM EventAlert WHERE eventalert_event_id IN ('.implode(',', $events).') and eventalert_duration > 0';
    $this->db->query($query);
    while($this->db->next_record()) {
      $alerts[$this->db->f('eventalert_event_id')][] = array('user' => $this->db->f('eventalert_user_id'), 'duration' => $this->db->f('eventalert_duration'));
    }
    return $alerts;
  }

  /**
   * @return Vcalendar
   */
  function & getDocument($method='PUBLISH') {
    $this->document = new Vcalendar();
    $this->setHeaders($method);
    foreach($this->eventSets as $set) {
      $id = $set['event_id'];      
      if(is_null($this->vevents[$id])) {
        $this->vevents[$id] = &$this->addVevent($set);
      }
    }

    $attendees = run_query_get_events_attendee(array_keys($this->vevents));
    while($attendees->next_record()) {
      $this->addAttendee($this->vevents[$attendees->f('event_id')] , $attendees->Record);
    }
    if(count($this->vevents) > 0) {
      $exceptions = run_query_get_events_exception(array_keys($this->vevents));
      while($exceptions->next_record()) {
        $date = new Of_Date($exceptions->f('eventexception_date'));
        $timezone = $exceptions->f('event_timezone');
        if ($timezone) $date->setOriginalTimeZone($timezone);
        $this->addExdate($this->vevents[$exceptions->f('eventexception_parent_id')] , $date);
        
        
        $enddate = clone $date;
        $enddate->addSecond(3600);
        $this->addExdate($this->vevents[$exceptions->f('eventexception_parent_id')] , $enddate);

      }    
    }
    return $this->document;
  }

  function setHeaders($method) {
    $vcalendar = &$this->document->vcalendar;
    $vcalendar->set('prodid','-//Aliasource Groupe LINAGORA//OBM Calendar '.$GLOBALS['obm_version'].'//FR');
    $vcalendar->set('calscale', 'GREGORIAN');
    $vcalendar->set('x-obm-time',time());
    $vcalendar->set('version','2.0');
    $vcalendar->set('method', strtoupper($method));
  }

  function & addVevent(&$data) {
    $vevent = &$this->document->createElement('vevent');
    $created = $this->parseDate($data['timecreate']);
    $created->setOriginalTimeZone($data['event_timezone']);
    $vevent->set('created', $created);

    $last_modified = $this->parseDate($data['timeupdate']);
    $last_modified->setOriginalTimeZone($data['event_timezone']);
    $vevent->set('last-modified', $last_modified);

    $dtstart = $this->parseDate($data['event_date']);
    $dtstart->setOriginalTimeZone($data['event_timezone']);
    $vevent->set('dtstart', $dtstart);
    $vevent->set('duration', $data['event_duration']);
    $vevent->set('transp', $data['event_opacity']);
    if($data['event_allday'] != 0) {
      $vevent->set('x-obm-all-day', 1);
    }
    $vevent->set('summary', $data['event_title']);
    $vevent->set('description', $data['event_description']);
    $vevent->set('class', $this->parsePrivacy($data['event_privacy']));
    $vevent->set('priority', $this->parsePriority($data['event_priority']));
    $vevent->set('organizer', $data['event_owner']);
    $vevent->set('location', $data['event_location']);
    $vevent->set('categories', array($data['eventcategory1_label']));
    $vevent->set('x-obm-color', $data['event_color']);
    if(!empty($this->alerts[$data['event_id']])) {
      foreach($this->alerts[$data['event_id']] as $alert) {
        $vevent->set('x-obm-alert', $alert);
      }
    }
    $vevent->set('uid', $data['event_ext_id']);
    if(!is_null($data['event_repeatkind']) && $data['event_repeatkind'] != 'none') {
      $vevent->set('rrule',$this->parseRrule($data));
    }
    $this->document->vcalendar->appendChild($vevent);
    return $vevent;
  }

  /**
   * @return Of_Date
   */
  function parseDate($timestamp) {
    return new Of_Date($timestamp, 'GMT');
  }
  
  function addAttendee(&$vevent, &$data) {
   $vevent->set('attendee',$this->parseAttendee($data['eventlink_entity_id'], $data['eventlink_entity'], $data['eventlink_state']));
  }
  
  function addExdate(&$vevent, &$date) {
   $vevent->set('exdate', $date);
  }
    
  function parsePrivacy($privacy) {
    if($privacy == 1) {
      return 'PRIVATE';
    }
    return 'PUBLIC';
  }

  function parsePriority($priority) {
    if($priority == 1) {
      return 9;
    } elseif ($priority == 2) {
      return 5;
    } elseif ($priority == 3) {
      return 1;
    }
  }

  function parseAttendee($id, $entity, $state) {
    return array('entity' => $entity, 'id' => $id, 'state' => $state);;
  }
  
  function parseRrule($data) {
    $rrule = array();
     switch ($data['event_repeatkind']) {
       case 'daily' :
       case 'yearly' :
         $rrule['kind'] = $data['event_repeatkind'];
         break;
       case 'monthlybydate' :
         $rrule['kind'] = 'monthly';
         break;
       case 'monthlybyday' :
         $rrule['kind'] = 'monthly';
         $date = new Of_Date($data['event_date'], 'GMT');
         $day = $date->get(Of_Date::WEEKDAY_ICS);
         
         $num =  ceil($date->getDay()/7);
         $rrule['byday'] = array($num.$day);
         break;
       case 'weekly' :
         $rrule['kind'] = 'weekly';
         foreach($this->weekDays as $longDay => $shortDay) {
           $index = date('w', strtotime($longDay));
           if($data['event_repeatdays'][$index] == '1') {
             $days[] = $shortDay;
           }           
         }
         $rrule['byday'] = $days;
         break;
     }
     if ($data['event_endrepeat'])
       $rrule['until'] = $this->parseDate($data['event_endrepeat']);
     $rrule['interval'] = $data['event_repeatfrequence'];
     return $rrule;
  }
}
?>
