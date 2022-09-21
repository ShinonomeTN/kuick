package com.shinonometn.kuick.config

import com.shinonometn.koemans.utils.resolveBaseName
import com.shinonometn.koemans.utils.splitToPair
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStreamReader
import java.net.URI

/** An more advance hocon configuration builder for ktor */
class ApplicationConfigurationFactory(
    private val args: Array<String> = emptyArray(),
    configurator: Configuration.() -> Unit = Configuration.DefaultConfig
) {
    private val config = Configuration().apply(configurator)
    private val logger = LoggerFactory.getLogger("ApplicationConfigurationFactory")

    val context = Context(args, logger)

    /** build the config */
    fun build(): Config {
        val defaultSettings = (config.defaultProviders.filterNotNull() + config.configurations)
            .map { context.it() }

        context.log.info("Use {} default setting configs.", defaultSettings.size)

        val configProviders = (context.argsMap["-config"]?.distinct() ?: emptyList())
            .plus(System.getenv("application_profiles").split(","))
            .mapNotNull { url -> (config.resolveConfiguration ?: ResolveConfiguration().apply(ResolveConfiguration.DefaultConfig)).resolve(url) }

        context.log.info("{} config providers.", configProviders.size)

        val configNames = config.configurationPolicy(context, configProviders.map { it.first })

        context.log.info("Enabled profile: {}", configNames)

        val configList = configProviders.filter { configNames.contains(it.first) }.map { it.second() }

        return (defaultSettings + configList).reversed().reduce { config, acc -> acc.withFallback(config) }.resolve()
    }

    private fun ResolveConfiguration.resolve(location: String): Pair<String, () -> Config>? {
        for (resolver in chain) return resolver.providerOrNull(URI.create(location)) ?: continue
        logger.info("No resolver for config '{}'", location)
        return null
    }

    /** Context that used for deciding configuration */
    class Context internal constructor(val args: Array<String>, val log: Logger) {
        //@formatter:off
        val argsMap = args.mapNotNull { it.splitToPair('=') }
            .groupBy { it.first }
            .mapValues { (_, value) -> value.map { it.second } }
        //@formatter:on

        fun arg(name: String): String? = argsMap[name]?.lastOrNull()

        fun args(name: String): List<String> = argsMap[name] ?: emptyList()

        val jar = argsMap["-jar"]?.firstOrNull()?.let {
            if (it.matches(jarProtocolPattern)) URI(it).toURL() else File(it).toURI().toURL()
        }

        companion object {
            private val jarProtocolPattern = Regex("^(file:|jrt:|jar:)(.+)")
        }
    }

    /** Configure profile resolvers */
    class ResolveConfiguration internal constructor() {
        internal val chain = mutableListOf<Resolver>()

        /** append new resolver */
        fun append(r: Resolver) = chain.add(r)

        companion object {
            /** the default config will use file and classpath resolver */
            val DefaultConfig: ResolveConfiguration.() -> Unit = {
                file()
                classpath()
            }

            /* File resolver */

            private const val fileProtocol = "file://"
            private val fileProviderSupportedPattern = Regex("^(${fileProtocol}.+)|((?!://).+)")
            private val fileProvider = Resolver {
                when {
                    it.matches(fileProviderSupportedPattern) -> ({ ConfigFactory.parseFile(File(it.removePrefix(fileProtocol))) })
                    else -> null
                }
            }

            /**
             * use file loader.
             * location with 'file://' or without protocol will be resolved.
             */
            fun ResolveConfiguration.file() = append(fileProvider)

            /* classpath resolver */

            private const val classPathProtocol = "classpath://"
            private val classpathProviderSupportedPattern = Regex("^${classPathProtocol}.+")
            private fun classpathProvider(classLoader: ClassLoader) = Resolver {
                if (it.matches(classpathProviderSupportedPattern)) {
                    classLoader.getResourceAsStream("/${it.removePrefix(classPathProtocol)}")
                        ?.let { stream -> { ConfigFactory.parseReader(InputStreamReader(stream)) } }
                } else null
            }

            /**
             * This method create a resolver with current thread's classloader
             */
            fun ResolveConfiguration.classpath() = append(classpathProvider(Thread.currentThread().contextClassLoader))

            /**
             * Use classpath resource loader
             * location with 'classpath://' will be resolved.
             * @param loader classloader be used
             */
            fun ResolveConfiguration.classpath(loader: ClassLoader) = append(classpathProvider(loader))
        }
    }

    class Configuration internal constructor() {
        internal val configurations = mutableListOf<Context.() -> Config>()
        internal val defaultProviders = Array<(Context.() -> Config)?>(3) { null }

        private val systemProperties by lazy { ConfigFactory.systemProperties() }

        /** use bundled application configuration, usually the application.conf in the classpath */
        fun useBundledApplicationConfiguration() {
            defaultProviders[0] = { ConfigFactory.load() }
        }

        /** use system properties that starts with 'ktor' */
        fun useKtorSystemProperties() {
            defaultProviders[1] = { systemProperties.withOnlyPath("ktor") }
        }

        private val propertyPattern = Regex("^-P:(.+)")

        /** use properties that starts with '-P:' in cmd line */
        fun useCommandLineProperties() {
            defaultProviders[2] = {
                ConfigFactory.parseMap(
                    argsMap.mapNotNull { (key, value) ->
                        propertyPattern.matchEntire(key)?.let {
                            it.groupValues.last() to (if (value.size <= 1) value.firstOrNull() else value)
                        }
                    }.toMap()
                )
            }
        }

        fun configProvider(provider: Context.() -> Config) = configurations.add(provider)

        internal var resolveConfiguration: ResolveConfiguration? = null

        /** config the profile resolver */
        fun resolvers(config: ResolveConfiguration.() -> Unit = ResolveConfiguration.DefaultConfig) {
            resolveConfiguration = ResolveConfiguration().apply(config)
        }

        internal var configurationPolicy: Context.(List<String>) -> List<String> = { it }

        /** setup the configuration policy. Policies will be chained together. */
        fun configurationPolicies(vararg policies: Context.(List<String>) -> List<String>) {
            configurationPolicy = {
                var result = it
                for (policy in policies) result = policy(result)
                result
            }
        }

        companion object {
            /** use system properties that starts with given [prefix] */
            fun Configuration.useSystemPropertyWithPrefix(prefix: String) = configProvider { systemProperties.withOnlyPath(prefix) }

            /**
             * The default setting
             * - use bundled application.conf
             * - use system properties starts with 'application' or 'ktor'
             * - use command line parameters starts with '-P:'
             * - enable file and classpath config loading
             * - enable profile by profile name (use '-profile=name' or env 'application_profiles')
             */
            val DefaultConfig: Configuration.() -> Unit = {
                useBundledApplicationConfiguration()
                useKtorSystemProperties()
                useCommandLineProperties()
                useSystemPropertyWithPrefix("application")

                configurationPolicies(ProfileSelectorPolicy)
            }

            private val conditionalProfilePattern = Regex("^application-([^.]+?)(?:\\..*)?$")

            /**
             * enable profiles with profile name
             * those profiles should start with 'application-', following a profile name.
             * e.g. application-dev.conf
             */
            val ProfileSelectorPolicy: Context.(List<String>) -> List<String> = { profileList ->
                val profiles = (argsMap["-profile"]?.distinct()?.toList() ?: emptyList()).flatMap {
                    if (it.contains(",")) it.split(",") else listOf(it)
                }

                profileList.filter {
                    when (val match = conditionalProfilePattern.matchEntire(it)) {
                        null -> true
                        else -> profiles.contains(match.groupValues.last())
                    }
                }
            }
        }
    }

    class Resolver(private val provider: (String) -> (() -> Config)?) {
        /**
         * get configuration by location
         * @return pair of config information, null if not support. first is name and second is value
         */
        fun providerOrNull(location: URI): Pair<String, () -> Config>? {
            val name = location.resolveBaseName()
            val config = provider(location.toString()) ?: return null
            return name to config
        }
    }
}