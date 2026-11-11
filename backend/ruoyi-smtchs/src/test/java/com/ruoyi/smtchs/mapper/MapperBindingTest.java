package com.ruoyi.smtchs.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper 接口与 XML 绑定校验。
 *
 * <p>MyBatis 不会在启动时校验「接口方法是否都有对应 statement」，
 * 缺少时只在调用瞬间抛 {@code BindingException: Invalid bound statement}。
 * 本测试在构建期就把它暴露出来：解析 XML 得到全部 statement id，
 * 再逐个比对接口方法，确保没有漏配。</p>
 *
 * <p>注意：XML 实际位于 ruoyi-admin/src/main/resources/mapper/smtchs，
 * 测试通过 {@code ../ruoyi-admin} 相对路径读取，随工程结构稳定。</p>
 *
 * @author ruoyi
 */
class MapperBindingTest
{
    /** XML 所在目录（相对 ruoyi-smtchs 模块根） */
    private static final String MAPPER_DIR = "../ruoyi-admin/src/main/resources/mapper/smtchs/";

    /** Mapper 接口清单 */
    private static final List<Class<?>> MAPPERS = Arrays.asList(
            SmtEmployeeMapper.class,
            SmtWorkTypeMapper.class,
            SmtPriceRuleMapper.class,
            SmtWorkRecordMapper.class,
            SmtRecordRevisionMapper.class,
            SmtSysConfigMapper.class
    );

    @Test
    @DisplayName("每个 Mapper 接口方法都有对应的 XML statement")
    void everyInterfaceMethodHasStatement() throws Exception
    {
        Configuration configuration = new Configuration();

        // 解析全部 Mapper XML
        int parsed = 0;
        for (Class<?> mapper : MAPPERS)
        {
            String resource = MAPPER_DIR + mapper.getSimpleName() + ".xml";
            try (InputStream in = new java.io.FileInputStream(resource))
            {
                XMLMapperBuilder builder = new XMLMapperBuilder(
                        in, configuration, resource, configuration.getSqlFragments());
                builder.parse();
                parsed++;
            }
        }
        assertTrue(parsed == MAPPERS.size(),
                "应解析全部 " + MAPPERS.size() + " 个 Mapper XML，实际 " + parsed);

        // 逐个接口方法核对
        StringBuilder missing = new StringBuilder();
        int checked = 0;
        for (Class<?> mapper : MAPPERS)
        {
            for (Method method : mapper.getDeclaredMethods())
            {
                // 排除编译器生成的桥接/合成方法
                if (method.isSynthetic() || method.isBridge())
                {
                    continue;
                }
                String statementId = mapper.getName() + "." + method.getName();
                MappedStatement statement = configuration.getMappedStatement(statementId, false);
                if (statement == null)
                {
                    missing.append("\n  - ").append(statementId);
                }
                checked++;
            }
        }

        assertTrue(missing.length() == 0,
                "以下接口方法缺少 XML statement（运行时会抛 Invalid bound statement）：" + missing);
        assertTrue(checked > 0, "应至少校验一个方法");
    }

    @Test
    @DisplayName("每个 Mapper XML 的 resultMap 都能解析出实体属性")
    void resultMapsResolve() throws Exception
    {
        Configuration configuration = new Configuration();
        for (Class<?> mapper : MAPPERS)
        {
            String resource = MAPPER_DIR + mapper.getSimpleName() + ".xml";
            try (InputStream in = new java.io.FileInputStream(resource))
            {
                XMLMapperBuilder builder = new XMLMapperBuilder(
                        in, configuration, resource, configuration.getSqlFragments());
                builder.parse();
            }
        }

        // 对每个 resultMap 校验其 type 可加载、property 在类上存在
        StringBuilder bad = new StringBuilder();
        int mapCount = 0;
        for (String id : configuration.getResultMapNames())
        {
            if (!id.startsWith("com.ruoyi.smtchs.mapper."))
            {
                continue;
            }
            mapCount++;
            org.apache.ibatis.mapping.ResultMap rm = configuration.getResultMap(id);
            assertNotNull(rm, "resultMap 应可解析：" + id);
            Class<?> type = rm.getType();
            assertNotNull(type, "resultMap 应有类型：" + id);

            for (org.apache.ibatis.mapping.ResultMapping mapping : rm.getResultMappings())
            {
                String property = mapping.getProperty();
                if (property == null || property.contains("."))
                {
                    continue;
                }
                if (!hasProperty(type, property))
                {
                    bad.append("\n  - ").append(id).append(" → ").append(type.getSimpleName())
                            .append(".").append(property);
                }
            }
        }
        assertTrue(mapCount > 0, "应至少发现一个 resultMap");
        assertTrue(bad.length() == 0, "以下 resultMap 属性在实体类上不存在：" + bad);
    }

    /** 判断类（含父类）是否有该属性（对应 getter 或字段） */
    private static boolean hasProperty(Class<?> type, String property)
    {
        String cap = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
        {
            for (Method m : c.getDeclaredMethods())
            {
                String name = m.getName();
                if (name.equals("get" + cap) || name.equals("is" + cap) || name.equals("set" + cap))
                {
                    return true;
                }
            }
            for (java.lang.reflect.Field f : c.getDeclaredFields())
            {
                if (f.getName().equals(property))
                {
                    return true;
                }
            }
        }
        return false;
    }
}
