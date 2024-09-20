package com.example.med.service;

import javax.validation.Valid;

import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.med.dao.MedicalPlanDao;


@Service
public class MedicalPlanServices {
	
	@Autowired
	private MedicalPlanDao dao;
	
	public boolean checkForDuplicateKey(String key) {
		if (dao.checkForDuplicatePlans(key)) {
			return true;
		}
		return false;
	}
	
	public void savePlan(JSONObject object) {
		dao.savePlan(object);
	}
	
	public String generateEtag(String plan) {
		return  DigestUtils.md5Hex(plan);
	}

	public void deletePlan(String ID) {
		dao.deletePlan(ID);
	}

	public String getPlan(String string) {
		return dao.getPlan(string).toString();
	}
	
	public String getEtag(String key) {
		return dao.getEtag(key);
	}

	public void patchPlan(String ID, @Valid String modifiedPlan) {
		dao.patchPlan(ID, modifiedPlan);
	}

	public void saveEtag(String key, String etag) {
		dao.saveEtag(key, etag);
	}
}
