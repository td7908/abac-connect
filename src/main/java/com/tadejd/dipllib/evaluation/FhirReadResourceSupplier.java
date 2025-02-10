package com.tadejd.dipllib.evaluation;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.model.primitive.IdDt;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * @author Tadej Delopst
 */

public class FhirReadResourceSupplier implements Supplier<IBaseResource> {

	private final IFhirResourceDao<? extends IBaseResource> resourceDao;
	private final String resourceName;
	private final String resourceId;

	private IBaseResource resource = null;
	private boolean read = false;

	private final ReentrantLock lock = new ReentrantLock();

	public FhirReadResourceSupplier(
		IFhirResourceDao<? extends IBaseResource> resourceDao,
		String resourceName,
		String resourceId) {
		this.resourceDao = resourceDao;
		this.resourceName = resourceName;
		this.resourceId = resourceId;
	}

	@Override
	public IBaseResource get() {
		lock.lock();
		try {
			if (!read) {
				try {
					resource = readResource();
				} catch (Exception e) {
					resource = null;
				}
				read = true;
			}
			return resource;
		} finally {
			lock.unlock();
		}
	}

	private IBaseResource readResource() {
		if (resourceDao != null) {
			return resourceDao.read(new IdDt(resourceName, resourceId), null);
		}
		return null;
	}
}
