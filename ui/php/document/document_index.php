<script language="php">
///////////////////////////////////////////////////////////////////////////////
// OBM - File : document_index.php                                            //
//     - Desc : Document Index File                                           //
// 2003-08-21 Rande Mehdi                                              //
///////////////////////////////////////////////////////////////////////////////
// $Id$ //
///////////////////////////////////////////////////////////////////////////////
// Actions              -- Parameter
// - index (default)    -- search fields  -- show the document search form
// - search             -- search fields  -- show the result set of search
///////////////////////////////////////////////////////////////////////////////

///////////////////////////////////////////////////////////////////////////////
// Session, Auth, Perms  Management                                          //
///////////////////////////////////////////////////////////////////////////////
$path = "..";
$section = "PROD";
$menu = "DOCUMENT";
$obminclude = getenv("OBM_INCLUDE_VAR");
if ($obminclude == "") $obminclude = "obminclude";
require("$obminclude/phplib/obmlib.inc");
include("$obminclude/global.inc");
page_open(array("sess" => "OBM_Session", "auth" => "OBM_Challenge_Auth", "perm" => "OBM_Perm"));
include("$obminclude/global_pref.inc");

require("document_query.inc");
require("document_display.inc");


page_close();
if ($action == "") $action = "index";
$document = get_param_document();
get_document_action();
$perm->check();

///////////////////////////////////////////////////////////////////////////////
// Main Program                                                              //
///////////////////////////////////////////////////////////////////////////////

if (! $document["popup"]) {
  $display["header"] = generate_menu($menu, $section); // Menu
}
///////////////////////////////////////////////////////////////////////////////
// Normal calls
///////////////////////////////////////////////////////////////////////////////
if ($action == "index" || $action == "") {
///////////////////////////////////////////////////////////////////////////////
  $cat1_q = run_query_documentcategory1();
  $cat2_q = run_query_documentcategory2();
  $mime_q = run_query_documentmime();
  $usr_q = run_query_userobm();
  $display["search"] = html_document_search_form($cat1_q, $cat2_q,$mime_q,  $document);
  if ($set_display == "yes") {
    $display["result"] = dis_document_search_list($document);
  } else {
    $display["msg"] = display_info_msg($l_no_display);
  }

} elseif ($action == "search")  {
///////////////////////////////////////////////////////////////////////////////
  $cat1_q = run_query_documentcategory1();
  $cat2_q = run_query_documentcategory2();
  $mime_q = run_query_documentmime();
  $usr_q = run_query_userobm();
  $display["search"] = html_document_search_form($cat1_q, $cat2_q,$mime_q,  $document);
  $display["result"] = dis_document_search_list($document);
  
} elseif ($action == "new")  {
///////////////////////////////////////////////////////////////////////////////
  require("document_js.inc");
  $cat1_q = run_query_documentcategory1();
  $cat2_q = run_query_documentcategory2();
  $mime_q = run_query_documentmime();
  $usr_q = run_query_userobm();
  $display["detail"] = html_document_form($action,"",$cat1_q, $cat2_q,$mime_q,  $document);
  
} elseif ($action == "detailconsult")  {
///////////////////////////////////////////////////////////////////////////////
  if ($param_document > 0) {
    $doc_q = run_query_detail($param_document);
    if ($doc_q->num_rows() == 1) {
      $display["detailInfo"] = display_record_info($doc_q->f("document_usercreate"),$doc_q->f("document_userupdate"),$doc_q->f("timecreate"),$doc_q->f("timeupdate")); 
      $display["detail"] = html_document_consult($doc_q);
    } else {
      $display["msg"] .= display_err_msg($l_query_error . " - " . $doc_q->query . " !");
    }
  }

} elseif ($action == "detailupdate")  {
///////////////////////////////////////////////////////////////////////////////
  if ($param_document > 0) {
    $doc_q = run_query_detail($param_document);
    if ($doc_q->num_rows() == 1) {
      $cat1_q = run_query_documentcategory1();
      $cat2_q = run_query_documentcategory2();
      $mime_q = run_query_documentmime();
      $usr_q = run_query_userobm();
      require("document_js.inc");
      $display["detailInfo"] = display_record_info($doc_q->f("document_usercreate"),$doc_q->f("document_userupdate"),$doc_q->f("timecreate"),$doc_q->f("timeupdate"));
      $display["detail"] = html_document_form($action,$doc_q,$cat1_q, $cat2_q,$mime_q,  $document);
  } else {
      $display["msg"] .= display_err_msg($l_query_error . " - " . $doc_q->query . " !");
    }
  }


} elseif ($action == "insert")  {
///////////////////////////////////////////////////////////////////////////////
  if (check_data_form("", $document)) {
    $retour = run_query_insert($document);
    if ($retour) {
      $display["msg"] .= display_ok_msg($l_insert_ok);
    } else {
      $display["msg"] .= display_err_msg($l_insert_error);
    }
    $cat1_q = run_query_documentcategory1();
    $cat2_q = run_query_documentcategory2();
    $mime_q = run_query_documentmime();
    $usr_q = run_query_userobm();
    $display["search"] = html_document_search_form($cat1_q, $cat2_q,$mime_q, $usr_q, $document);
    $display["result"] = dis_document_search_list($document);
  // Form data are not valid
  } else {
    require("document_js.inc");
    $display["msg"] = display_warn_msg($l_invalid_data . " : " . $err_msg);
    $cat1_q = run_query_documentcategory1();
    $cat2_q = run_query_documentcategory2();
    $mime_q = run_query_documentmime();
    $usr_q = run_query_userobm();
    $display["detail"] = html_document_form($action,"",$cat1_q, $cat2_q,$mime_q, $usr_q, $document);
  }

} elseif ($action == "update")  {
///////////////////////////////////////////////////////////////////////////////
  if (check_data_form($param_document, $document)) {
    $retour = run_query_update($param_document, $document);
    if ($retour) {
      $display["msg"] .= display_ok_msg($l_update_ok);
    } else {
      $display["msg"] .= display_err_msg($l_update_error);
    }
    $doc_q = run_query_detail($param_document);
    $display["detailInfo"] .= display_record_info($doc_q->f("document_usercreate"),$doc_q->f("document_userupdate"),$doc_q->f("timecreate"),$doc_q->f("timeupdate")); 
    $display["detail"] = html_document_consult($doc_q);
  } else {
    require("document_js.inc");
    $display["msg"] = display_warn_msg($l_invalid_data . " : " . $err_msg);
    $cat1_q = run_query_documentcategory1();
    $cat2_q = run_query_documentcategory2();
    $mime_q = run_query_documentmime();
    $usr_q = run_query_userobm();
    $display["detail"] = html_document_form($action,"",$cat1_q, $cat2_q,$mime_q, $usr_q, $document);
  }

} elseif ($action == "admin")  {
///////////////////////////////////////////////////////////////////////////////
  require("document_js.inc");
  $display["detail"] = dis_admin_index();

} elseif ($action == "cat1_insert")  {
///////////////////////////////////////////////////////////////////////////////
  $retour = run_query_cat1_insert($document);
  if ($retour) {
    $display["msg"] .= display_ok_msg($l_cat1_insert_ok);
  } else {
    $display["msg"] .= display_err_msg($l_cat1_insert_error);
  }
  require("document_js.inc");
  $display["detail"] .= dis_admin_index();

} elseif ($action == "cat1_update")  {
///////////////////////////////////////////////////////////////////////////////
  $retour = run_query_cat1_update($document);
  if ($retour) {
    $display["msg"] .= display_ok_msg($l_cat1_update_ok);
  } else {
    $display["msg"] .= display_err_msg($l_cat1_update_error);
  }
  require("document_js.inc");
  $display["detail"] .= dis_admin_index();

} elseif ($action == "cat1_checklink")  {
///////////////////////////////////////////////////////////////////////////////
  $display["detail"] .= dis_cat1_links($document);

} elseif ($action == "cat1_delete")  {
///////////////////////////////////////////////////////////////////////////////
  $retour = run_query_cat1_delete($document["category1"]);
  if ($retour) {
    $display["msg"] .= display_ok_msg($l_cat1_delete_ok);
  } else {
    $display["msg"] .= display_err_msg($l_cat1_delete_error);
  }
  require("document_js.inc");
  $display["detail"] .= dis_admin_index();

} elseif ($action == "cat2_insert")  {
///////////////////////////////////////////////////////////////////////////////
  $retour = run_query_cat2_insert($document);
  if ($retour) {
    $display["msg"] .= display_ok_msg($l_cat2_insert_ok);
  } else {
    $display["msg"] .= display_err_msg($l_cat2_insert_error);
  }
  require("document_js.inc");
  $display["detail"] .= dis_admin_index();

} elseif ($action == "cat2_update")  {
///////////////////////////////////////////////////////////////////////////////
  $retour = run_query_cat2_update($document);
  if ($retour) {
    $display["msg"] .= display_ok_msg($l_cat2_update_ok);
  } else {
    $display["msg"] .= display_err_msg($l_cat2_update_error);
  }
  require("document_js.inc");
  $display["detail"] .= dis_admin_index();

} elseif ($action == "cat2_checklink")  {
///////////////////////////////////////////////////////////////////////////////
  $display["detail"] .= dis_cat2_links($document);

} elseif ($action == "cat2_delete")  {
///////////////////////////////////////////////////////////////////////////////
  $retour = run_query_cat2_delete($document["category2"]);
  if ($retour) {
    $display["msg"] .= display_ok_msg($l_cat2_delete_ok);
  } else {
    $display["msg"] .= display_err_msg($l_cat2_delete_error);
  }
  require("document_js.inc");
  $display["detail"] .= dis_admin_index();

} elseif ($action == "mime_insert")  {
///////////////////////////////////////////////////////////////////////////////
  $retour = run_query_mime_insert($document);
  if ($retour) {
    $display["msg"] .= display_ok_msg($l_mime_insert_ok);
  } else {
    $display["msg"] .= display_err_msg($l_mime_insert_error);
  }
  require("document_js.inc");
  $display["detail"] .= dis_admin_index();

} elseif ($action == "mime_update")  {
///////////////////////////////////////////////////////////////////////////////
  $retour = run_query_mime_update($document);
  if ($retour) {
    $display["msg"] .= display_ok_msg($l_mime_update_ok);
  } else {
    $display["msg"] .= display_err_msg($l_mime_update_error);
  }
  require("document_js.inc");
  $display["detail"] .= dis_admin_index();

} elseif ($action == "mime_checklink")  {
///////////////////////////////////////////////////////////////////////////////
  $display["detail"] .= dis_mime_links($document);

} elseif ($action == "mime_delete")  {
///////////////////////////////////////////////////////////////////////////////
  $retour = run_query_mime_delete($document["mime"]);
  if ($retour) {
    $display["msg"] .= display_ok_msg($l_mime_delete_ok);
  } else {
    $display["msg"] .= display_err_msg($l_mime_delete_error);
  }
  require("document_js.inc");
  $display["detail"] .= dis_admin_index();

}  elseif ($action == "display") {
///////////////////////////////////////////////////////////////////////////////
  $pref_q = run_query_display_pref($auth->auth["uid"], "document", 1);
  $display["detail"] = dis_document_display_pref($pref_q);

} else if ($action == "dispref_display") {
///////////////////////////////////////////////////////////////////////////////
  run_query_display_pref_update($entity, $fieldname, $disstatus);
  $pref_q = run_query_display_pref($auth->auth["uid"], "document", 1);
  $display["detail"] = dis_document_display_pref($pref_q);

} else if ($action == "dispref_level") {
///////////////////////////////////////////////////////////////////////////////
  run_query_display_pref_level_update($entity, $new_level, $fieldorder);
  $pref_q = run_query_display_pref($auth->auth["uid"], "document", 1);
  $display["detail"] = dis_document_display_pref($pref_q);
}
///////////////////////////////////////////////////////////////////////////////
// Display
///////////////////////////////////////////////////////////////////////////////
$display["head"] = display_head($l_document);
$display["end"] = display_end();
display_page($display);


///////////////////////////////////////////////////////////////////////////////
// Stores Company parameters transmited in $document hash
// returns : $document hash with parameters set
///////////////////////////////////////////////////////////////////////////////
function get_param_document() {
  global $tf_title, $tf_author, $tf_path,$tf_mime,$tf_filename;
  global $tf_cat1,$tf_cat2,$tf_extension,$tf_mimetype;
  global $param_document;
  global $sel_cat1, $sel_cat2,$sel_mime,$cb_privacy;

  if (isset ($param_document)) $document["id"] = $param_document;
  
  if (isset ($tf_title)) $document["title"] = $tf_title;
  if (isset ($tf_author)) $document["author"] = $tf_author;
  if (isset ($tf_path)) $document["path"] = $tf_path;
  if (isset ($tf_filename)) $document["filename"] = $tf_filename;

  $document["size"] = 5000;
  $document["name"] = "heu.nimp";

  
  if (isset ($tf_cat1)) $document["cat1_label"] = $tf_cat1;
  if (isset ($tf_cat2)) $document["cat2_label"] = $tf_cat2;
  if (isset ($tf_mime)) $document["mime_label"] = $tf_mime;
  if (isset ($tf_extension)) $document["extension"] = $tf_extension;
  if (isset ($tf_mimetype)) $document["mimetype"] = $tf_mimetype;

  if (isset ($sel_cat1)) $document["category1"] = $sel_cat1;
  if (isset ($sel_cat2)) $document["category2"] = $sel_cat2;
  if (isset ($sel_mime)) $document["mime"] = $sel_mime;

  if (isset ($cb_privacy)) $document["privacy"] = $cb_privacy;

  if (debug_level_isset($cdg_param)) {
    if ( $document ) {
      while ( list( $key, $val ) = each( $document ) ) {
        echo "<br />document[$key]=$val";
      }
    }
  }

  return $document;
}


///////////////////////////////////////////////////////////////////////////////
//  Company Action 
///////////////////////////////////////////////////////////////////////////////
function get_document_action() {
  global $document, $actions, $path;
  global $l_header_find,$l_header_new,$l_header_update,$l_header_delete;
  global $l_header_consult, $l_header_display,$l_header_admin;
  global $document_read, $document_write, $document_admin_read, $document_admin_write;

// Index
  $actions["DOCUMENT"]["index"] = array (
    'Name'     => $l_header_find,
    'Url'      => "$path/document/document_index.php?action=index",
    'Right'    => $document_read,
    'Condition'=> array ('all') 
                                    	 );

// Search
  $actions["DOCUMENT"]["search"] = array (
    'Url'      => "$path/document/document_index.php?action=search",
    'Right'    => $document_read,
    'Condition'=> array ('None') 
                                    	 );


// New
  $actions["DOCUMENT"]["new"] = array (
    'Name'     => $l_header_new,
    'Url'      => "$path/document/document_index.php?action=new",
    'Right'    => $document_write,
    'Condition'=> array ('search','index','detailconsult','insert','update','admin','display') 
                                     );


// Detail Consult
  $actions["DOCUMENT"]["detailconsult"]  = array (
    'Name'     => $l_header_consult,
    'Url'      => "$path/document/document_index.php?action=detailconsult&amp;param_document=".$document["id"]."",
    'Right'    => $document_read,
    'Condition'=> array ('detailupdate') 
                                     		 );

// Detail Update
  $actions["DOCUMENT"]["detailupdate"] = array (
    'Name'     => $l_header_update,
    'Url'      => "$path/document/document_index.php?action=detailupdate&amp;param_document=".$document["id"]."",
    'Right'    => $document_write,
    'Condition'=> array ('detailconsult', 'update') 
                                     	      );



// Insert
  $actions["DOCUMENT"]["insert"] = array (
    'Url'      => "$path/document/document_index.php?action=insert",
    'Right'    => $document_write,
    'Condition'=> array ('None') 
                                     	 );  


// Admin
  $actions["DOCUMENT"]["admin"] = array (
    'Name'     => $l_header_admin,
    'Url'      => "$path/document/document_index.php?action=admin",
    'Right'    => $document_admin_read,
    'Condition'=> array ('all') 
   					);

// Category Insert
  $actions["DOCUMENT"]["cat1_insert"] = array (
    'Url'      => "$path/document/document_index.php?action=cat1_insert",
    'Right'    => $document_admin_write,
    'Condition'=> array ('None') 
                                     	     );

// Category Update
  $actions["DOCUMENT"]["cat1_update"] = array (
    'Url'      => "$path/document/document_index.php?action=cat1_update",
    'Right'    => $document_admin_write,
    'Condition'=> array ('None') 
                                     	      );

// Category Check Link
  $actions["DOCUMENT"]["cat1_checklink"] = array (
    'Url'      => "$path/document/document_index.php?action=cat1_checklink",
    'Right'    => $document_admin_write,
    'Condition'=> array ('None') 
                                     		);

// Category Delete
  $actions["DOCUMENT"]["cat1_delete"] = array (
    'Url'      => "$path/document/document_index.php?action=cat1_delete",
    'Right'    => $document_admin_write,
    'Condition'=> array ('None') 
                                     	       );

// Category Insert
  $actions["DOCUMENT"]["cat2_insert"] = array (
    'Url'      => "$path/document/document_index.php?action=cat2_insert",
    'Right'    => $document_admin_write,
    'Condition'=> array ('None') 
                                     	     );

// Category Update
  $actions["DOCUMENT"]["cat2_update"] = array (
    'Url'      => "$path/document/document_index.php?action=cat2_update",
    'Right'    => $document_admin_write,
    'Condition'=> array ('None') 
                                     	      );

// Category Check Link
  $actions["DOCUMENT"]["cat2_checklink"] = array (
    'Url'      => "$path/document/document_index.php?action=cat2_checklink",
    'Right'    => $document_admin_write,
    'Condition'=> array ('None') 
                                     		);

// Category Delete
  $actions["DOCUMENT"]["cat2_delete"] = array (
    'Url'      => "$path/document/document_index.php?action=cat2_delete",
    'Right'    => $document_admin_write,
    'Condition'=> array ('None') 
                                     	       );

// Mime Insert
  $actions["DOCUMENT"]["mime_insert"] = array (
    'Url'      => "$path/document/document_index.php?action=mime_insert",
    'Right'    => $document_admin_write,
    'Condition'=> array ('None') 
                                     	     );

// Mime Update
  $actions["DOCUMENT"]["mime_update"] = array (
    'Url'      => "$path/document/document_index.php?action=mime_update",
    'Right'    => $document_admin_write,
    'Condition'=> array ('None') 
                                     	      );

// Mime Check Link
  $actions["DOCUMENT"]["mime_checklink"] = array (
    'Url'      => "$path/document/document_index.php?action=mime_checklink",
    'Right'    => $document_admin_write,
    'Condition'=> array ('None') 
                                     		);

// Mime Delete
  $actions["DOCUMENT"]["mime_delete"] = array (
    'Url'      => "$path/document/document_index.php?action=mime_delete",
    'Right'    => $document_admin_write,
    'Condition'=> array ('None') 
                                     	       );
  // Display
  $actions["DOCUMENT"]["display"] = array (
    'Name'     => $l_header_display,
    'Url'      => "$path/document/document_index.php?action=display",
    'Right'    => $document_read,
    'Condition'=> array ('all') 
                                      	 );

// Display Préférences
  $actions["DOCUMENT"]["dispref_display"] = array (
    'Url'      => "$path/document/document_index.php?action=dispref_display",
    'Right'    => $document_read,
    'Condition'=> array ('None') 
                                     		 );

// Display Level
  $actions["DOCUMENT"]["dispref_level"]  = array (
    'Url'      => "$path/document/document_index.php?action=dispref_level",
    'Right'    => $document_read,
    'Condition'=> array ('None') 
                                     		 );
}


