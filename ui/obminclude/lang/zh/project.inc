﻿<script language="php">
///////////////////////////////////////////////////////////////////////////////
// OBM - Language : English                                                  //
//     - File     : projet.inc                                               //
// 2003-07-10 Aliacom - Bastien Continsouzas                                 //
///////////////////////////////////////////////////////////////////////////////
// $Id$ //
///////////////////////////////////////////////////////////////////////////////

$l_tasktype = "类型"; //"Category"
$l_task = "任务"; //"Task"
$l_soldtime = "售出时间"; //"Sold time"
$l_estimatedtime = "预估时间"; //"Estimated time"
$l_shortname = "简称"; //"Short name"
$l_sold = "已售出"; //"Sold"
$l_estimated = "已预估"; //"Estimated"
$l_allotted = "已分配"; //"Allocated"
$l_projected = "已规划"; //"Projected"
$l_used = "已使用"; //"Used"
$l_missing = "Missing"; //"Missing"
$l_alloestimated = "预计时间分配"; //"Estimated time allocation"
$l_usedestimated = "预计时间使用"; //"Estimated time usage"
$l_progress = "项目进展"; //"Project progress"
$l_progeval = "提前/延迟评测"; //"Advance/Delay evaluation"
$l_parent = "父任务"; //"Parent task"
$l_soldtime = "售出时间"; //"Sold time"
$l_projtime = "规划时间"; //"Projected time"
$l_total = "总共"; //"Total"
$l_resume = "总结"; //"Summary"
$l_tasks = "任务"; //"Tasks"
$l_reftask_manage = "管理关联任务"; //"Manage reference tasks"
$l_delete_reftask = "您要删除一个关联任务，确定继续吗？";  //"You are about to delete a Reference Task, do you want to continue?"
$l_reftask = '关联任务'; //'Reference Task'
$l_ref = "关联"; //"Reference"
$l_project_date = "日期"; //"Date"
$l_project_duration = "持续时间"; //"Duration"
$l_project_desc = "描述"; //"Description"
$l_project_tech = "技术说明"; //"Technical description"
$l_closing = 'Closing'; //'Closing'
$l_closing_no = 'No closing done for this project'; //'No closing done for this project'
$l_members_info = "项目成员"; //"Project members"
$l_progressinfos = "项目评测：时间(已使用/missing/已规划)"; //"Progress evaluation : time (used / missing / projected)"
$l_memberstimeinfos = "请填写被分配到任务中的每个人的预计工作时间（按天计算）"; //"Fill in the working time (in days) projected for each person assigned to a task"
$l_fill_advance_form = "请填写工作时间：计划/剩余"; //"Fill in the working time : planned / remaining"
$l_task_new = "新任务"; //"New task"
$l_task_update = "更新任务"; //"Update task"
$l_task_list = "已定义的任务";  //Defined tasks
$l_progress_info = "Fill in the working time (in days) missing for each person to complete a task"; //"Fill in the working time (in days) missing for each person to complete a task"
$l_select_project = "选择一个项目"; //"Select a project"
$l_mail_project_subject = "OBM : 项目"; //"OBM : Project"
$l_mail_project_member = "您被指派到该项目: "; //"You have been assigned to the project :"
$l_insert = "创建项目"; //"Create the project";
$l_update = "更新项目"; //"Update the project"
$l_delete = "删除项目";  //"Delete project"
$l_progress_update = "更新项目的进展"; //"Update the project progress"
$l_task_add = "添加任务";  //"Add task"
$l_task_upd = "删除任务"; //"Update task"
$l_task_del = "删除已选中的任务"; //"Delete selected tasks"
$l_closing_insert = "Save the closing"; //"Save the closing"
$l_closing_update = "Closing update"; //"Closing update"
$l_closing_delete = "Delete the closing"; //"Delete the closing"
$l_no_display = "填写搜索字段并点击<查找>搜索项目。"; //"Fill in the search fields and press < Find > to retrieve the projects."
$l_found = "个项目已找到。"; //"project(s) found."
$l_no_found = "未找到与查询匹配的项目！";  //"No project matching the query has been found!"
$l_no_tasks = "没有已定义的任务"; //"No tasks defined"
$l_no_members = "没有已定义的成员"; //"No defined members "
$l_no_allocation = "该项目成员还没有被指派到任务当中。"; //"The project members have not been assigned to a task yet"
$l_link_timetask = "该项目已经被添加到时间管理当中"; //"The project is used in time management"
$l_link_timetask_no = "该项目没有被添加到时间管理当中"; //"The project is not used in time management"
$l_cant_delete = "该项目无法被删除！";  //"The project cannot be deleted !"
$l_can_delete = "该项目可以被删除。"; //"The project can be deleted."
$l_no_task_del = "没有要删除的任务"; //"No task to remove"
$l_ptask_same_task = "该父任务不能与此任务相同！";  //"The parent task cannot be the same as the task !"
$l_ptask_and_parent = "一个父任务是不能有父任务的！";  //"A parent task cannot have a parent !"
$l_ptask_has_parent = "被作为父任务的任务现在有一个任务！"; //"The task given as parent has a parent now !"
$l_ptask_has_user = "该任务已被作为父任务指派给一个用户！";  //"The task given as parent is assigned to a user !"
$l_del_member_sel = "删除这些成员"; //"Remove these members"
$l_member_added = "个成员已添加"; //"member(s) added"
$l_member_removed = "个成员已删除"; //"member(s) removed"
$l_no_member_add = "没有成员需要添加"; //"No member to add"
$l_no_member_del = "没有成员需要删除"; //"No member to remove"
$l_add_member = "添加成员到该项目"; //"Add members to the project"
$l_board_no_data = "若缺少任务或成员的定义，控制台则无法显示！";  //"Dashboard cannot be displayed if tasks or members definitions are missing !"
$l_select_task = "您必须选择一个任务"; //"You have to select a task"
$l_fill_name = "提交前，您必须填写名称。"; //"You have to fill in the name before submitting."
$l_fill_tt = "提交前，您必须填写类型。"; //"You have to fill in the category before submitting."
$l_fill_soldtime = "提交前，您必须填写已售出时间。"; //"You have to fill in the sold time before submitting."
$l_fill_estimated = "提交前，您必须填写预计时间。";  //"You have to fill in the estimated time before submitting."
$l_fill_tasklabel = " 您必须填写该任务的名字。";  //"You have to fill in the name for this task."
$l_fill_soldnumber = "售出时间必须是一个数。"; //"The sold time number is not valid."
$l_fill_projected = "每个成员都必须被指派到一个任务中"; //"Each member must be assigned to a task"
$l_fill_projnumber = "预计时间必须是一个数。"; //"The expected time number is not valid."
$l_fill_missing = "The missing time must be filled in for every member"; //"The missing time must be filled in for every member"
$l_fill_missnumber = "The missing time number is not valid."; //"The missing time number is not valid."
$l_tolong_shortname = "简称太长"; //"The short name is too long"
$l_err_used = "已使用的时间无效！"; //"Used time is invalid !"
$l_err_remaining = "剩余时间无效！"; //"Remaining time is invalid !"
$l_delete_confirm = "确认删除该项目？"; //"Confirm the project deletion ?"
$l_project_display = "显示项目列表选项"; //"Display options for lists of projects"
