/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (c) 1997-2008 Aliasource - Groupe LINAGORA
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation; either version 2 of the
 *  License, (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 * 
 *  http://www.obm.org/                                              
 * 
 * ***** END LICENSE BLOCK ***** */
package fr.aliacom.obm.common.user;

import java.util.Date;

import fr.aliacom.obm.common.domain.ObmDomain;

public class ObmUser {

	private int uid;
	private String login;
	private String lastName;
	private String firstName;
	private String email;

	private String address1;
	private String address2;
	private String address3;

	private String expresspostal;
	private String homePhone;
	private String mobile;
	private String service;
	private String title;
	private String town;
	private String workFax;
	private String workPhone;
	private String zipCode;
	private String description;

	private Date timeCreate;
	private Date timeUpdate;
	private ObmUser createdBy;
	private ObmUser updatedBy;

	private ObmDomain domain;
	private boolean publicFreeBusy;

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getAddress1() {
		return address1;
	}

	public void setAddress1(String address1) {
		this.address1 = address1;
	}

	public String getAddress2() {
		return address2;
	}

	public void setAddress2(String address2) {
		this.address2 = address2;
	}

	public String getAddress3() {
		return address3;
	}

	public void setAddress3(String address3) {
		this.address3 = address3;
	}

	public String getExpresspostal() {
		return expresspostal;
	}

	public void setExpresspostal(String expresspostal) {
		this.expresspostal = expresspostal;
	}

	public String getHomePhone() {
		return homePhone;
	}

	public void setHomePhone(String homePhone) {
		this.homePhone = homePhone;
	}

	public String getMobile() {
		return mobile;
	}

	public void setMobile(String mobile) {
		this.mobile = mobile;
	}

	public String getService() {
		return service;
	}

	public void setService(String service) {
		this.service = service;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getTown() {
		return town;
	}

	public void setTown(String town) {
		this.town = town;
	}

	public String getWorkFax() {
		return workFax;
	}

	public void setWorkFax(String workFax) {
		this.workFax = workFax;
	}

	public String getWorkPhone() {
		return workPhone;
	}

	public void setWorkPhone(String workPhone) {
		this.workPhone = workPhone;
	}

	public String getZipCode() {
		return zipCode;
	}

	public void setZipCode(String zipCode) {
		this.zipCode = zipCode;
	}

	public ObmDomain getDomain() {
		return domain;
	}

	public void setDomain(ObmDomain domain) {
		this.domain = domain;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstname) {
		this.firstName = firstname;
	}

	public int getUid() {
		return uid;
	}

	public void setUid(int uid) {
		this.uid = uid;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastname) {
		this.lastName = lastname;
	}

	public String getLogin() {
		return login;
	}

	public void setLogin(String login) {
		this.login = login;
	}

	public Date getTimeCreate() {
		return timeCreate;
	}

	public void setTimeCreate(Date timeCreate) {
		this.timeCreate = timeCreate;
	}

	public Date getTimeUpdate() {
		return timeUpdate;
	}

	public void setTimeUpdate(Date timeUpdate) {
		this.timeUpdate = timeUpdate;
	}

	public ObmUser getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(ObmUser createdBy) {
		this.createdBy = createdBy;
	}

	public ObmUser getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(ObmUser updatedBy) {
		this.updatedBy = updatedBy;
	}

	public boolean isPublicFreeBusy() {
		return publicFreeBusy;
	}

	public void setPublicFreeBusy(boolean publicFreeBusy) {
		this.publicFreeBusy = publicFreeBusy;
	}
}