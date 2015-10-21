package com.togogo.common;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * 
 * 简单类dao
 * @author Zhanhao Wong
 *
 */
public class CommonDao {
	
	private final static String INSERT_SQL =  "insert into %s (%s) values (%s)";
	
	/**
	 * 通用保存方法
	 * @param obj
	 */
	public void save(Object obj) {
		if(obj == null) {
			throw new NullPointerException("保存对象不能为空");
		}
		
		try {
			
			// 获得类对象
			Class clazz = obj.getClass();
			
			// 打开连接
			Connection conn = DbKit.me.getConnection();
			
			// 生成sql
			String sql =  getPreInsertSql(clazz);
			System.out.println("生成sql：" + sql);
			
			// 预备stat对象
			PreparedStatement stat = conn.prepareStatement(sql);
			
			// 填充值
			setPreInsertVal(stat, obj);
			
			// 执行
			stat.execute();
			
			// 关闭连接等，释放资源
			DbKit.me.closeState(stat);
			DbKit.me.closeConnection(conn);
			
			
		} catch (Exception e) {
			throw new RuntimeException("保存失败",e);
		}
	}
	
	
	/**
	 * 通用查询方法
	 * @param entityClazz
	 * @param sql
	 * @param value
	 * @return
	 */
	public <T> List<T> find(Class<T> entityClazz, String select, String where, Object... value) {
		try {
			// 打开连接
			Connection conn = DbKit.me.getConnection();
			
			// 预备stat对象
			PreparedStatement stat = conn.prepareStatement(select + " " + where);
			
			// 填充值
			for(int i = 0; i < value.length; i++) {
				stat.setObject(i+1, value[i]);
			}
			
			// 执行查询
			ResultSet query = stat.executeQuery();
			
			// 把查询结果封装成对象
			return resultToObj(query, entityClazz);
			
		} catch (Exception e) {
			throw new RuntimeException("查询失败！" , e);
		}
		
	}
	
	
	

	/**
	 * 把结果封装成对象
	 * @param query
	 * @param entityClazz
	 * @return
	 */
	private <T> List<T> resultToObj(ResultSet query, Class<T> entityClazz) {
		List<T> list = new ArrayList<T>();
		try {
			Map<String, Class> cloumnsMeta = getColumnsMeta(query);
			Method[] methods = entityClazz.getMethods();
			
			while(query.next()) {
				T obj = entityClazz.newInstance();
				Set<String> columnNames = cloumnsMeta.keySet();
				for(String name : columnNames) {
					String seMethodName = "set" +name;
					for(Method m : methods) {
						if(m.getName().equalsIgnoreCase(seMethodName)) {
							m.invoke(obj, query.getObject(name));
							break;
						}
					}
					
				}
				list.add(obj);
				
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return list;
		
	}


	/**
	 * 获得结果的列名和类型
	 * @param query
	 * @return
	 */
	private Map<String, Class> getColumnsMeta(ResultSet query) {
		Map<String, Class> cloumnsMeta = null;
		try {
			cloumnsMeta = new HashMap<String, Class>();
			ResultSetMetaData meta = query.getMetaData();
			int count = meta.getColumnCount();
			for(int i = 1; i <= count; i++) {
				cloumnsMeta.put(meta.getColumnName(i), Class.forName(meta.getColumnClassName(i)));
			}
		
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return cloumnsMeta;
		
	}


	/**
	 * 通过反射
	 * 给PreparedStatement赋值
	 * @param stat
	 * @param obj
	 */
	private void setPreInsertVal(PreparedStatement stat, Object obj) {
		Class clazz = obj.getClass();
		Method[] methods = clazz.getMethods();
		List<Method> columns = new ArrayList<Method>();
		for(Method m : methods) {
			String name = m.getName();
			if(name.startsWith("get") && !name.equals("getClass")) {
				columns.add(m);
			}
		}
		
		try {
			for(int i = 0; i < columns.size(); i++) {
				stat.setObject(i+1, columns.get(i).invoke(obj, new Object[]{}));
			}
		} catch (Exception e) {
			throw new RuntimeException("赋值Statement失败",e);
		}
		
	}

	/**
	 * 通过反射
	 * 把实体类中的 getXXX 后面的 XXX 提取出来作为列组装成插入的sql语句
	 * @param clazz
	 * @return
	 */
	private String getPreInsertSql(Class clazz) {
		Method[] methods = clazz.getMethods();
		List<String> columns = new ArrayList<String>();
		for(Method m : methods) {
			String name = m.getName();
			if(name.startsWith("get") && !name.equals("getClass")) {
				columns.add((name.charAt(3)+"").toLowerCase() + name.substring(4));
			}
		}
		
		if(columns.isEmpty()) {
			return null;
		}
		
		StringBuilder columnsSb = new StringBuilder();
		StringBuilder placeSb = new StringBuilder();
		
		for(String c : columns) {
			columnsSb.append("," + c);
			placeSb.append(", ?");
		}
		
		return String.format(INSERT_SQL, clazz.getSimpleName(), columnsSb.substring(1), placeSb.substring(1));
	}

}
