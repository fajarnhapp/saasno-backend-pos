package com.saasno.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

@Repository
public abstract class JpaRepository<E, K> {

	private final Class<E> entityClass;

	public JpaRepository(Class<E> entityClass) {
		this.entityClass = entityClass;
	}

	public E find(K id) {
		if (id != null) {
			return getEntityManager().find(entityClass, id);
		} else {
			return null;
		}
	}

	public E findLock(K id) {
		if (id != null) {
			return getEntityManager().find(entityClass, id, LockModeType.PESSIMISTIC_WRITE);
		} else {
			return null;
		}
	}

	public List<E> findAll() throws JsonProcessingException {
		return findAll(null, null);
	}

	public List<E> findAll(Integer firstResult, Integer maxResult) throws JsonProcessingException {
		TypedQuery<E> queryFind = getEntityManager().createQuery("SELECT e FROM " + getEntityName() + " e",
				entityClass);
		if (firstResult != null) {
			queryFind.setFirstResult(firstResult);
		}
		if (maxResult != null) {
			queryFind.setMaxResults(maxResult);
		}
		List<E> result = queryFind.getResultList();
		return result;
	}

	public abstract EntityManager getEntityManager();

	protected String getEntityName() {
		return entityClass.getSimpleName();
	}

	@Transactional
	public void store(E entity) {
		try {
			getEntityManager().persist(entity);
		} catch (Exception e) {
			Log.error("Error store : " + e);
			throw e;
		}
	}

	public E update(E entity) {
		try {
			return getEntityManager().merge(entity);
		} catch (Exception e) {
			Log.error("Error update : " + e);
			throw e;
		}
	}

	public void delete(E entity) {
		try {
			getEntityManager().remove(getEntityManager().merge(entity));
			getEntityManager().flush();
		} catch (Exception e) {
			Log.error("Error delete : " + e);
			throw new ApiException("500", "Data masih dipakai");
		}
	}
}
