<SCRIPT language="php">
///////////////////////////////////////////////////////////////////////////////
// OBM - File : company_index.php                                            //
//     - Desc : Company Index File                                           //
// 1999-03-19 Pierre Baudracco                                               //
///////////////////////////////////////////////////////////////////////////////
// $Id$ //
///////////////////////////////////////////////////////////////////////////////
// Actions           -- Parameter
// - index (default) -- search fields  -- show the company search form
// - search          -- search fields  -- show the result set of search
// - new             --                -- show the new company form
// - detailconsult   -- $param_company -- show the company detail
// - detailupdate    -- $param_company -- show the company detail form
// - insert          -- form fields    -- insert the company
// - update          -- form fields    -- update the company
// - check_delete    -- $param_company -- check links before delete
// - delete          -- $hd_company_id -- delete the company
// - admin           --                -- admin index (kind)
// - kind_insert     -- form fields    -- insert the kind
// - kind_update     -- form fields    -- update the kind
// - kind_checklink  --                -- check if kind is used
// - kind_delete     -- $sel_kind      -- delete the kind
// - display         --                -- display and set display parameters
// - dispref_display --                -- update one field display value
// - dispref_level   --                -- update one field display position 
// External API ---------------------------------------------------------------
// - ext_get_id      -- $title         -- select a company (return id) 
// - ext_get_id_url  -- $url, $title   -- select a company (id), load URL 
///////////////////////////////////////////////////////////////////////////////


///////////////////////////////////////////////////////////////////////////////
// Session, Auth, Perms  Management                                          //
///////////////////////////////////////////////////////////////////////////////
$path = "..";
$section = "COM";
$menu = "COMPANY";
$obminclude = getenv("OBM_INCLUDE_VAR");
if ($obminclude == "") $obminclude = "obminclude";
require("$obminclude/phplib/obmlib.inc");
include("$obminclude/global.inc");
page_open(array("sess" => "OBM_Session", "auth" => "OBM_Challenge_Auth", "perm" => "OBM_Perm"));
include("$obminclude/global_pref.inc");

require("company_query.inc");
require("company_display.inc");

// updating the company bookmark : 
if ( ($param_company == $last_company) && (strcmp($action,"delete")==0) ) {
  $last_company=$last_company_default;
} else if ( ($param_company >0 ) && ($last_company != $param_company) ) {
  $last_company=$param_company;
  run_query_set_user_pref($auth->auth["uid"],"last_company",$param_company);
  $last_company_name = run_query_global_company_name($last_company);
  //$sess->register("last_company");
}

page_close();
if($action == "") $action = "index";
$company = get_param_company();
get_company_action();
$perm->check();
///////////////////////////////////////////////////////////////////////////////
// Beginning of HTML Page                                                    //
///////////////////////////////////////////////////////////////////////////////
display_head($l_company);     // Head & Body

if ($popup) {
///////////////////////////////////////////////////////////////////////////////
// External calls (main menu not displayed)                                  //
///////////////////////////////////////////////////////////////////////////////
  if ($action == "ext_get_id") {
    require("company_js.inc");
    $comp_q = run_query_company();
    html_select_company($comp_q, stripslashes($title));
  } elseif ($action == "ext_get_id_url") {
    require("company_js.inc");
    $comp_q = run_query_company();
    html_select_company($comp_q, stripslashes($title), $url);
  } else {
    display_error_permission();
  }

  display_end();
  exit();
}

///////////////////////////////////////////////////////////////////////////////
// Main Program                                                              //
///////////////////////////////////////////////////////////////////////////////
generate_menu($menu,$section);         // Menu
display_bookmarks();

if ($action == "index" || $action == "") {
///////////////////////////////////////////////////////////////////////////////
  $type_q = run_query_companytype();
  $usr_q = run_query_userobm();
  html_company_search_form($type_q, $usr_q, $company);
  if ($set_display == "yes") {
    dis_company_search_list($company);
  } else {
    display_ok_msg($l_no_display);
  }

} elseif ($action == "search")  {
///////////////////////////////////////////////////////////////////////////////
  $type_q = run_query_companytype();
  $usr_q = run_query_userobm();
  html_company_search_form($type_q, $usr_q, $company);
  dis_company_search_list($company);

} elseif ($action == "new")  {
///////////////////////////////////////////////////////////////////////////////
  if ($auth->auth["perm"] != $perms_user) {
    $type_q = run_query_companytype();
    $usr_q = run_query_userobm();
    require("company_js.inc");
    html_company_form($action,"",$type_q, $usr_q, $company);
  } 
  else {
    display_error_permission();
  }
} elseif ($action == "detailconsult")  {
///////////////////////////////////////////////////////////////////////////////
  if ($param_company > 0) {
    $obm_q_soc = run_query_detail($param_company);
    if ($obm_q_soc->num_rows() == 1) {
      $obm_q_type = run_query_companytype();
      display_record_info($obm_q_soc->f("company_usercreate"),$obm_q_soc->f("company_userupdate"),$obm_q_soc->f("timecreate"),$obm_q_soc->f("timeupdate")); 
      html_company_consult($obm_q_soc, $obm_q_type);
    } else {
      display_err_msg($l_query_error . " - " . $obm_q_soc->query . " !");
    }
  }

} elseif ($action == "detailupdate")  {
///////////////////////////////////////////////////////////////////////////////
  if ($param_company > 0) {
    $comp_q = run_query_detail($param_company);
    if ($comp_q->num_rows() == 1) {
      $type_q = run_query_companytype();
      $usr_q = run_query_userobm();
      require("company_js.inc");
      display_record_info($comp_q->f("company_usercreate"),$comp_q->f("company_userupdate"),$comp_q->f("timecreate"),$comp_q->f("timeupdate"));
      html_company_form($action, $comp_q, $type_q, $usr_q, $company);
    } else {
      display_err_msg($l_query_error . " - " . $comp_q->query . " !");
    }
  }

} elseif ($action == "insert")  {
///////////////////////////////////////////////////////////////////////////////
  if (check_data_form("", $company)) {

    // If the context (same companies) was confirmed ok, we proceed
    if ($hd_confirm == $c_yes) {
      $retour = run_query_insert($company);
      if ($retour) {
        display_ok_msg($l_insert_ok);
      } else {
        display_err_msg($l_insert_error);
      }
      $type_q = run_query_companytype();
      $usr_q = run_query_userobm();
      html_company_search_form($type_q, $usr_q, $company);

    // If it is the first try, we warn the user if some companies seem similar
    } else {
      $obm_q = check_company_context("", $company);
      if ($obm_q->num_rows() > 0) {
        dis_company_warn_insert("", $obm_q, $company);
      } else {
        $retour = run_query_insert($company);
        if ($retour) {
          display_ok_msg($l_insert_ok);
        } else {
          display_err_msg($l_insert_error);
        }
        $type_q = run_query_companytype();
        $usr_q = run_query_userobm();
        html_company_search_form($type_q, $usr_q, $company);
      }
    }

  // Form data are not valid
  } else {
    display_warn_msg($l_invalid_data . " : " . $err_msg);
    $type_q = run_query_companytype();
    $usr_q = run_query_userobm();
    html_company_form($action, "", $type_q, $usr_q, $company);
  }

} elseif ($action == "update")  {
///////////////////////////////////////////////////////////////////////////////
  if (check_data_form($param_company, $company)) {
    $retour = run_query_update($param_company, $company);
    if ($retour) {
      display_ok_msg($l_update_ok);
    } else {
      display_err_msg($l_update_error);
    }
    $obm_q_type = run_query_companytype();
    $obm_q_soc = run_query_detail($param_company);
    display_record_info($obm_q_soc->f("company_usercreate"),$obm_q_soc->f("company_userupdate"),$obm_q_soc->f("timecreate"),$obm_q_soc->f("timeupdate")); 
    html_company_consult($obm_q_soc, $obm_q_type);
  } else {
    display_warn_msg($l_invalid_data . " : " . $err_msg);
    $type_q = run_query_companytype();
    $usr_q = run_query_userobm();
    html_company_form($action, "", $type_q, $usr_q, $company);
  }

} elseif ($action == "check_delete")  {
///////////////////////////////////////////////////////////////////////////////
  require("company_js.inc");
  dis_check_links($param_company);

} elseif ($action == "delete")  {
///////////////////////////////////////////////////////////////////////////////
  $retour = run_query_delete($hd_company_id);
  if ($retour) {
    display_ok_msg($l_delete_ok);
  } else {
    display_err_msg($l_delete_error);
  }

  $type_q = run_query_companytype();
  $usr_q = run_query_userobm();
  html_company_search_form($type_q, $usr_q, $company);

} elseif ($action == "admin")  {
///////////////////////////////////////////////////////////////////////////////
  if ($auth->auth["perm"] != $perms_user) {
    $obm_q_type = run_query_companytype();
    require("company_js.inc");
    html_company_kind_form($obm_q_type);
  } 
  else {
    display_error_permission();
  }

} elseif ($action == "kind_insert")  {
///////////////////////////////////////////////////////////////////////////////
  $retour = run_query_kind_insert($tf_kind_new);
  if ($retour) {
    display_ok_msg($l_kind_insert_ok);
  } else {
    display_err_msg($l_kind_insert_error);
  }

  // Affichage admin
  $obm_q_type = run_query_companytype();
  require("company_js.inc");
  html_company_kind_form($obm_q_type);

} elseif ($action == "kind_update")  {
///////////////////////////////////////////////////////////////////////////////
  $retour = run_query_kind_update($tf_kind_upd, $sel_kind);
  if ($retour) {
    display_ok_msg($l_kind_update_ok);
  } else {
    display_err_msg($l_kind_update_error);
  }
  // Affichage admin
  $obm_q_type = run_query_companytype();
  require("company_js.inc");
  html_company_kind_form($obm_q_type);

} elseif ($action == "kind_checklink")  {
///////////////////////////////////////////////////////////////////////////////
  dis_kind_links($sel_kind);

  // Affichage admin
  $obm_q_type = run_query_companytype();
  require("company_js.inc");
  html_company_kind_form($obm_q_type);

} elseif ($action == "kind_delete")  {
///////////////////////////////////////////////////////////////////////////////
  $retour = run_query_kind_delete($hd_kind_label);
  if ($retour) {
    display_ok_msg($l_kind_delete_ok);
  } else {
    display_err_msg($l_kind_delete_error);
  }

  // Affichage admin
  $obm_q_type = run_query_companytype();
  require("company_js.inc");
  html_company_kind_form($obm_q_type);


}  elseif ($action == "display") {
/////////////////////////////////////////////////////////////////////////
  $pref_q = run_query_display_pref($auth->auth["uid"], "company", 1);
  dis_company_display_pref($pref_q);

} else if ($action == "dispref_display") {
/////////////////////////////////////////////////////////////////////////
  run_query_display_pref_update($entity, $fieldname, $display);
  $pref_q = run_query_display_pref($auth->auth["uid"], "company", 1);
  dis_company_display_pref($pref_q);

} else if ($action == "dispref_level") {
/////////////////////////////////////////////////////////////////////////
  run_query_display_pref_level_update($entity, $new_level, $fieldorder);
  $pref_q = run_query_display_pref($auth->auth["uid"], "company", 1);
  dis_company_display_pref($pref_q);
}


///////////////////////////////////////////////////////////////////////////////
// Stores Company parameters transmited in $company hash
// returns : $company hash with parameters set
///////////////////////////////////////////////////////////////////////////////
function get_param_company() {
  global $tf_num, $cb_state, $tf_name, $sel_kind, $tf_ad1, $tf_ad2, $tf_zip;
  global $tf_town, $tf_cdx, $tf_ctry, $tf_phone, $tf_fax, $tf_web, $tf_email;
  global $sel_market, $ta_com, $param_company;
  global $cdg_param;

  if (isset ($param_company)) $company["id"] = $param_company;
  if (isset ($tf_num)) $company["num"] = $tf_num;
  if (isset ($cb_state)) $company["state"] = $cb_state;
  if (isset ($tf_name)) $company["name"] = $tf_name;
  if (isset ($sel_kind)) $company["kind"] = $sel_kind;
  if (isset ($sel_market)) $company["marketing_manager"] = $sel_market;
  if (isset ($tf_ad1)) $company["ad1"] = $tf_ad1;
  if (isset ($tf_ad2)) $company["ad2"] = $tf_ad2;
  if (isset ($tf_zip)) $company["zip"] = $tf_zip;
  if (isset ($tf_town)) $company["town"] = $tf_town;
  if (isset ($tf_cdx)) $company["cdx"] = $tf_cdx;
  if (isset ($tf_ctry)) $company["ctry"] = $tf_ctry;
  if (isset ($tf_phone)) $company["phone"] = $tf_phone;
  if (isset ($tf_fax)) $company["fax"] = $tf_fax;
  if (isset ($tf_web)) $company["web"] = $tf_web;
  if (isset ($tf_email)) $company["email"] = $tf_email;
  if (isset ($ta_com)) $company["com"] = $ta_com;

  if (debug_level_isset($cdg_param)) {
    if ( $company ) {
      while ( list( $key, $val ) = each( $company ) ) {
        echo "<BR>company[$key]=$val";
      }
    }
  }

  return $company;
}

///////////////////////////////////////////////////////////////////////////////
//  Company Action 
///////////////////////////////////////////////////////////////////////////////

function get_company_action() {
  global $company,$actions;
  global $l_header_find,$l_header_new_f,$l_header_modify,$l_header_delete;
  global $l_header_display,$l_header_admin;
  global $company_read, $company_write, $company_admin_read, $company_admin_write;
//Index

  $actions["COMPANY"]["index"] = array (
    'Name'     => $l_header_find,
    'Url'      => "$path/company/company_index.php?action=index",
    'Right'    => $company_read,
    'Condition'=> array ('all') 
                                    	 );

//Search

  $actions["COMPANY"]["search"] = array (
    'Url'      => "$path/company/company_index.php?action=search",
    'Right'    => $company_read,
    'Condition'=> array ('None') 
                                    	 );

//New

  $actions["COMPANY"]["new"] = array (
    'Name'     => $l_header_new_f,
    'Url'      => "$path/company/company_index.php?action=new",
    'Right'    => $company_write,
    'Condition'=> array ('search','index','detailconsult','admin','display') 
                                     );

//Detail Consult

  $actions["COMPANY"]["detailconsult"]  = array (
    'Url'      => "$path/company/company_index.php?action=detailconsult",
    'Right'    => $company_write,
    'Condition'=> array ('None') 
                                     		 );

//Detail Update

  $actions["COMPANY"]["detailupdate"] = array (
    'Name'     => $l_header_modify,
    'Url'      => "$path/company/company_index.php?action=detailupdate&amp;param_company=".$company["id"]."",
    'Right'    => $company_write,
    'Condition'=> array ('detailconsult') 
                                     	      );

//Insert

  $actions["COMPANY"]["insert"] = array (
    'Url'      => "$path/company/company_index.php?action=insert",
    'Right'    => $company_write,
    'Condition'=> array ('None') 
                                     	 );

//Update

  $actions["COMPANY"]["update"] = array (
    'Url'      => "$path/company/company_index.php?action=update",
    'Right'    => $company_write,
    'Condition'=> array ('None') 
                                     	 );

//Check Delete

  $actions["COMPANY"]["check_delete"] = array (
    'Name'     => $l_header_delete,
    'Url'      => "$path/company/company_index.php?action=check_delete&amp;param_company=".$company["id"]."",
    'Right'    => $company_write,
    'Condition'=> array ('detailconsult') 
                                     	      );

//Delete

  $actions["COMPANY"]["delete"] = array (
    'Url'      => "$path/company/company_index.php?action=delete",
    'Right'    => $company_write,
    'Condition'=> array ('None') 
                                     	 );

//Admin

  $actions["COMPANY"]["admin"] = array (
    'Name'     => $l_header_admin,
    'Url'      => "$path/company/company_index.php?action=admin",
    'Right'    => $company_admin_read,
    'Condition'=> array ('all') 
                                       );

//Kind Insert

  $actions["COMPANY"]["kind_insert"] = array (
    'Url'      => "$path/company/company_index.php?action=kind_insert",
    'Right'    => $company_admin_write,
    'Condition'=> array ('None') 
                                     	     );

//Kind Update

  $actions["COMPANY"]["kind_update"] = array (
    'Url'      => "$path/company/company_index.php?action=kind_insert",
    'Right'    => $company_admin_write,
    'Condition'=> array ('None') 
                                     	      );

//Kind Check Link

  $actions["COMPANY"]["kind_checklink"] = array (
    'Url'      => "$path/company/company_index.php?action=kind_checklink",
    'Right'    => $company_admin_write,
    'Condition'=> array ('None') 
                                     		);

//Kind Delete

  $actions["COMPANY"]["kind_delete"] = array (
    'Url'      => "$path/company/company_index.php?action=kind_delete",
    'Right'    => $company_admin_write,
    'Condition'=> array ('None') 
                                     	       );

//Display

  $actions["COMPANY"]["display"] = array (
    'Name'     => $l_header_display,
    'Url'      => "$path/company/company_index.php?action=display",
    'Right'    => $company_read,
    'Condition'=> array ('all') 
                                      	 );

//Display Préférences

  $actions["COMPANY"]["dispref_display"] = array (
    'Url'      => "$path/company/company_index.php?action=dispref_display",
    'Right'    => $company_read,
    'Condition'=> array ('None') 
                                     		 );

//Display Level
  $actions["COMPANY"]["dispref_level"]  = array (
    'Url'      => "$path/company/company_index.php?action=dispref_level",
    'Right'    => $company_read,
    'Condition'=> array ('None') 
                                     		 );
}
///////////////////////////////////////////////////////////////////////////////
// Display end of page                                                       //
///////////////////////////////////////////////////////////////////////////////
display_end();


</SCRIPT>
