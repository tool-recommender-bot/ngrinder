package com.nhncorp.ngrinder.script.dao.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.stereotype.Repository;

import com.nhncorp.ngrinder.core.NGrinderConstants;
import com.nhncorp.ngrinder.core.util.ReflectionUtil;
import com.nhncorp.ngrinder.script.dao.ScriptDao;
import com.nhncorp.ngrinder.script.dao.ScriptsCache;
import com.nhncorp.ngrinder.script.model.Script;
import com.nhncorp.ngrinder.script.util.ScriptUtil;

/**
 * Script dao file implement
 * 
 * @author Liu Zhifei
 * @date 2012-6-13
 */
@Repository
public class ScriptDaoImpl implements ScriptDao {

	private static final Logger LOG = LoggerFactory.getLogger(ScriptDaoImpl.class);

	// private final Map<Long, Script> scriptsCache = new
	// ConcurrentHashMap<Long, Script>();

	@PostConstruct
	public void init() {
		this.findAll();

	}

	@Override
	public List<Script> findAll() {
		List<Script> scripts = new ArrayList<Script>();
		File root = new File(NGrinderConstants.PATH_SCRIPT);
		File[] scriptDirs = root.listFiles();
		if (null != scriptDirs && scriptDirs.length > 0) {
			for (File scriptDir : scriptDirs) {
				long id = 0;
				try {
					id = Long.valueOf(scriptDir.getName().substring(NGrinderConstants.PREFIX_SCRIPT.length()));
				} catch (NumberFormatException e) {
					continue;
				}
				Script script = this.findOne(id);
				scripts.add(script);
			}
		}
		return scripts;
	}

	@Override
	public Page<Script> getScripts(String searchStr, Pageable pageable) {
		Page<Script> page = null;

		List<Script> scripts = new ArrayList<Script>();
		List<Script> scriptCache = ScriptsCache.getInstance().get();

		if (null != pageable) {
			final Sort sort = pageable.getSort();
			this.sortScripts(scriptCache, sort);
		}

		if (null != searchStr) {
			searchStr = searchStr.toLowerCase();
		}

		int i = 0;
		for (Script script : scriptCache) {
			if (null != searchStr && !script.getFileName().toLowerCase().contains(searchStr)
					&& !script.getTags().toString().toLowerCase().contains(searchStr)
					&& !script.getLastModifiedUser().toLowerCase().contains(searchStr)) {
				continue;
			}

			i++;
			if (null != pageable && (i <= (pageable.getOffset() - pageable.getPageSize()) || i > pageable.getOffset())) {
				continue;
			}

			scripts.add(script);
		}

		page = new PageImpl<Script>(scripts, pageable, scriptCache.size());

		return page;
	}

	private void sortScripts(List<Script> scripts, final Sort sort) {
		if (null == sort) {
			return;
		}
		Comparator<Script> comparator = new Comparator<Script>() {

			@Override
			public int compare(Script s1, Script s2) {
				int result = 0;
				Iterator<Order> iterator = sort.iterator();
				while (iterator.hasNext()) {
					Order order = iterator.next();
					Direction direction = order.getDirection();
					String property = order.getProperty();
					Object o1 = ReflectionUtil.getFieldValue(s1, property);
					Object o2 = ReflectionUtil.getFieldValue(s2, property);

					String c1 = null != o1 ? o1.toString() : "";
					String c2 = null != o2 ? o2.toString() : "";

					result = c1.compareToIgnoreCase(c2);
					if (0 != result) {
						if (direction == Direction.DESC) {
							result = -result;
						}
						break;
					}
				}
				return result;
			}

		};
		Collections.sort(scripts, comparator);
	}

	@Override
	public Script findOne(long id) {

		Script script = ScriptsCache.getInstance().get(id);

		if (null == script) {

			String scriptPath = ScriptUtil.getScriptPath(id);
			String scriptPropertiesPath = scriptPath + NGrinderConstants.SCRIPT_PROPERTIES;

			FileInputStream fis = null;
			ObjectInputStream ois = null;
			try {
				fis = new FileInputStream(new File(scriptPropertiesPath));
				ois = new ObjectInputStream(fis);
				script = (Script) ois.readObject();
				ScriptsCache.getInstance().put(script);
			} catch (IOException e) {
				LOG.error("Deserialize Script bean failed.", e);
			} catch (ClassNotFoundException e) {
				LOG.error("Cast the class to Script failed.", e);
			} finally {
				IOUtils.closeQuietly(fis);
				IOUtils.closeQuietly(ois);
			}
		}

		return script;

	}

	@Override
	public void save(Script script) {
		if (script.getId() == 0) {
			script.setId(script.hashCode());
			ScriptUtil.createScriptPath(script.getId());
		}
		String scriptPath = ScriptUtil.getScriptPath(script.getId());

		String scriptPropertiesPath = scriptPath + NGrinderConstants.SCRIPT_PROPERTIES;
		FileOutputStream fos = null;
		ObjectOutputStream oos = null;
		try {
			fos = new FileOutputStream(scriptPropertiesPath);
			oos = new ObjectOutputStream(fos);
			oos.writeObject(script);
			ScriptsCache.getInstance().put(script);
		} catch (IOException e) {
			LOG.error("Serialize Script bean failed.", e);
		} finally {
			IOUtils.closeQuietly(fos);
			IOUtils.closeQuietly(oos);
		}
	}

	@Override
	public void delete(long id) {
		ScriptsCache.getInstance().remove(id);
	}

}
