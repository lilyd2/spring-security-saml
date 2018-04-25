/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.springframework.security.saml2.init;

import javax.xml.datatype.Duration;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;
import net.shibboleth.utilities.java.support.xml.DOMTypeSupport;
import net.shibboleth.utilities.java.support.xml.XMLParserException;
import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.XMLObjectBuilder;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallerFactory;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.common.SAMLObjectBuilder;
import org.opensaml.saml.common.SignableSAMLObject;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.Extensions;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.IndexedEndpoint;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.NameIDFormat;
import org.opensaml.saml.saml2.metadata.RoleDescriptor;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SingleLogoutService;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.SecurityException;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.UsageType;
import org.opensaml.security.credential.impl.KeyStoreCredentialResolver;
import org.opensaml.xmlsec.SignatureSigningParameters;
import org.opensaml.xmlsec.config.DefaultSecurityConfigurationBootstrap;
import org.opensaml.xmlsec.keyinfo.KeyInfoGenerator;
import org.opensaml.xmlsec.keyinfo.NamedKeyInfoGeneratorManager;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureSupport;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.opensaml.xmlsec.signature.support.Signer;
import org.springframework.security.saml2.metadata.Binding;
import org.springframework.security.saml2.metadata.Endpoint;
import org.springframework.security.saml2.metadata.Metadata;
import org.springframework.security.saml2.metadata.NameID;
import org.springframework.security.saml2.metadata.Provider;
import org.springframework.security.saml2.metadata.ServiceProvider;
import org.springframework.security.saml2.metadata.SsoProvider;
import org.springframework.security.saml2.signature.AlgorithmMethod;
import org.springframework.security.saml2.signature.Canonicalization;
import org.springframework.security.saml2.signature.DigestMethod;
import org.springframework.security.saml2.util.InMemoryKeyStore;
import org.springframework.security.saml2.xml.SimpleKey;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.Objects.isNull;
import static org.springframework.util.StringUtils.hasText;

public class OpenSamlConfiguration extends SpringSecuritySaml {

    private BasicParserPool parserPool;

    public OpenSamlConfiguration() {
        this.parserPool = new BasicParserPool();
    }

    public BasicParserPool getParserPool() {
        return parserPool;
    }

    void bootstrap() {
        //configure default values
        //maxPoolSize = 5;
        parserPool.setMaxPoolSize(50);
        //coalescing = true;
        parserPool.setCoalescing(true);
        //expandEntityReferences = false;
        parserPool.setExpandEntityReferences(false);
        //ignoreComments = true;
        parserPool.setIgnoreComments(true);
        //ignoreElementContentWhitespace = true;
        parserPool.setIgnoreElementContentWhitespace(true);
        //namespaceAware = true;
        parserPool.setNamespaceAware(true);
        //schema = null;
        parserPool.setSchema(null);
        //dtdValidating = false;
        parserPool.setDTDValidating(false);
        //xincludeAware = false;
        parserPool.setXincludeAware(false);

        Map<String, Object> builderAttributes = new HashMap<>();
        parserPool.setBuilderAttributes(builderAttributes);

        Map<String, Boolean> parserBuilderFeatures = new HashMap<>();
        parserBuilderFeatures.put("http://apache.org/xml/features/disallow-doctype-decl", TRUE);
        parserBuilderFeatures.put("http://javax.xml.XMLConstants/feature/secure-processing", TRUE);
        parserBuilderFeatures.put("http://xml.org/sax/features/external-general-entities", FALSE);
        parserBuilderFeatures.put("http://apache.org/xml/features/validation/schema/normalized-value", FALSE);
        parserBuilderFeatures.put("http://xml.org/sax/features/external-parameter-entities", FALSE);
        parserPool.setBuilderFeatures(parserBuilderFeatures);

        try {
            parserPool.initialize();
        } catch (ComponentInitializationException x) {
            throw new RuntimeException("Unable to initialize OpenSaml v3 ParserPool", x);
        }


        try {
            InitializationService.initialize();
        } catch (InitializationException e) {
            throw new RuntimeException("Unable to initialize OpenSaml v3", e);
        }

        XMLObjectProviderRegistry registry;
        synchronized (ConfigurationService.class) {
            registry = ConfigurationService.get(XMLObjectProviderRegistry.class);
            if (registry == null) {
                registry = new XMLObjectProviderRegistry();
                ConfigurationService.register(XMLObjectProviderRegistry.class, registry);
            }
        }

        registry.setParserPool(parserPool);
    }


    public XMLObjectBuilderFactory getBuilderFactory() {
        return XMLObjectProviderRegistrySupport.getBuilderFactory();
    }

    public MarshallerFactory getMarshallerFactory() {
        return XMLObjectProviderRegistrySupport.getMarshallerFactory();
    }

    public UnmarshallerFactory getUnmarshallerFactory() {
        return XMLObjectProviderRegistrySupport.getUnmarshallerFactory();
    }

    public EntityDescriptor getEntityDescriptor() {
        XMLObjectBuilderFactory builderFactory = getBuilderFactory();
        SAMLObjectBuilder<EntityDescriptor> builder =
            (SAMLObjectBuilder<EntityDescriptor>) builderFactory.getBuilder(EntityDescriptor.DEFAULT_ELEMENT_NAME);
        return builder.buildObject();
    }

    public SPSSODescriptor getSPSSODescriptor() {
        SAMLObjectBuilder<SPSSODescriptor> builder =
            (SAMLObjectBuilder<SPSSODescriptor>) getBuilderFactory().getBuilder(SPSSODescriptor.DEFAULT_ELEMENT_NAME);
        return builder.buildObject();
    }

    public IDPSSODescriptor getIDPSSODescriptor() {
        SAMLObjectBuilder<IDPSSODescriptor> builder =
            (SAMLObjectBuilder<IDPSSODescriptor>) getBuilderFactory().getBuilder(IDPSSODescriptor.DEFAULT_ELEMENT_NAME);
        return builder.buildObject();
    }

    public Extensions getMetadataExtensions() {
        SAMLObjectBuilder<Extensions> builder =
            (SAMLObjectBuilder<Extensions>) getBuilderFactory().getBuilder(Extensions.DEFAULT_ELEMENT_NAME);
        return builder.buildObject();
    }

    public NameIDFormat getNameIDFormat(NameID nameID) {
        SAMLObjectBuilder<NameIDFormat> builder =
            (SAMLObjectBuilder<NameIDFormat>) getBuilderFactory().getBuilder(NameIDFormat.DEFAULT_ELEMENT_NAME);
        NameIDFormat format = builder.buildObject();
        format.setFormat(nameID.toString());
        return format;
    }

    public SingleSignOnService getSingleSignOnService(Endpoint endpoint, int index) {
        SAMLObjectBuilder<SingleSignOnService> builder =
            (SAMLObjectBuilder<SingleSignOnService>) getBuilderFactory().getBuilder(SingleSignOnService.DEFAULT_ELEMENT_NAME);
        SingleSignOnService sso = builder.buildObject();
        sso.setLocation(endpoint.getLocation());
        sso.setBinding(endpoint.getBinding().toString());
        return sso;
    }

    public AssertionConsumerService getAssertionConsumerService(Endpoint endpoint, int index) {
        SAMLObjectBuilder<AssertionConsumerService> builder =
            (SAMLObjectBuilder<AssertionConsumerService>) getBuilderFactory().getBuilder(AssertionConsumerService.DEFAULT_ELEMENT_NAME);
        AssertionConsumerService consumer = builder.buildObject();
        consumer.setLocation(endpoint.getLocation());
        consumer.setBinding(endpoint.getBinding().toString());
        consumer.setIsDefault(endpoint.isDefault());
        consumer.setIndex(index);
        return consumer;
    }

    public SingleLogoutService getSingleLogoutService(Endpoint endpoint) {
        SAMLObjectBuilder<SingleLogoutService> builder =
            (SAMLObjectBuilder<SingleLogoutService>) getBuilderFactory().getBuilder(SingleLogoutService.DEFAULT_ELEMENT_NAME);
        SingleLogoutService service = builder.buildObject();
        service.setBinding(endpoint.getBinding().toString());
        service.setLocation(endpoint.getLocation());
        return service;
    }

    public KeyDescriptor getKeyDescriptor(SimpleKey key) {
        SAMLObjectBuilder<KeyDescriptor> builder =
            (SAMLObjectBuilder<KeyDescriptor>) getBuilderFactory().getBuilder(KeyDescriptor.DEFAULT_ELEMENT_NAME);
        KeyDescriptor descriptor = builder.buildObject();

        KeyStoreCredentialResolver resolver = getCredentialsResolver(key);
        Credential credential = getCredential(key, resolver);
        try {
            KeyInfo info = getKeyInfoGenerator(credential).generate(credential);
            descriptor.setKeyInfo(info);
            if (key.getType() != null) {
                descriptor.setUse(UsageType.valueOf(key.getType().toString()));
            }
            return descriptor;
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public Credential getCredential(SimpleKey key, KeyStoreCredentialResolver resolver) {
        try {
            CriteriaSet cs = new CriteriaSet();
            EntityIdCriterion criteria = new EntityIdCriterion(key.getAlias());
            cs.add(criteria);
            return resolver.resolveSingle(cs);
        } catch (ResolverException e) {
            throw new RuntimeException("Can't obtain SP private key", e);
        }
    }

    public KeyStoreCredentialResolver getCredentialsResolver(SimpleKey key) {
        InMemoryKeyStore ks = InMemoryKeyStore.fromKey(key);
        Map<String, String> passwords = hasText(key.getPrivateKey()) ?
            Collections.singletonMap(key.getAlias(), key.getPassphrase()) :
            Collections.emptyMap();
        KeyStoreCredentialResolver resolver = new KeyStoreCredentialResolver(
            ks.getKeyStore(),
            passwords
        );
        return resolver;
    }

    public KeyInfoGenerator getKeyInfoGenerator(Credential credential) {
        NamedKeyInfoGeneratorManager manager = DefaultSecurityConfigurationBootstrap.buildBasicKeyInfoGeneratorManager();
        return manager.getDefaultManager().getFactory(credential).newInstance();
    }

    public void signObject(SignableSAMLObject signable,
                           SimpleKey key,
                           AlgorithmMethod algorithm,
                           DigestMethod digest) {

        KeyStoreCredentialResolver resolver = getCredentialsResolver(key);
        Credential credential = getCredential(key, resolver);

        XMLObjectBuilder<Signature> signatureBuilder =
            (XMLObjectBuilder<Signature>) getBuilderFactory().getBuilder(Signature.DEFAULT_ELEMENT_NAME);
        Signature signature = signatureBuilder.buildObject(Signature.DEFAULT_ELEMENT_NAME);

        signable.setSignature(signature);


        SignatureSigningParameters parameters = new SignatureSigningParameters();
        parameters.setSigningCredential(credential);
        parameters.setKeyInfoGenerator(getKeyInfoGenerator(credential));
        parameters.setSignatureAlgorithm(algorithm.toString());
        parameters.setSignatureReferenceDigestMethod(digest.toString());
        parameters.setSignatureCanonicalizationAlgorithm(Canonicalization.ALGO_ID_C14N_EXCL_OMIT_COMMENTS.toString());

        try {
            SignatureSupport.prepareSignatureParams(signature, parameters);
            Marshaller marshaller = XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(signable);
            marshaller.marshall(signable);
            Signer.signObject(signature);
        } catch (SecurityException | MarshallingException | SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    public void validateSignature(SignableSAMLObject object, List<SimpleKey> keys) {
        try {
            SimpleKey key = keys.get(0);
            KeyStoreCredentialResolver resolver = getCredentialsResolver(key);
            Credential credential = getCredential(key, resolver);
            SignatureValidator.validate(object.getSignature(), credential);
        } catch (SignatureException e) {
            throw new org.springframework.security.saml2.signature.SignatureException(e.getMessage(), e);
        }
    }

    public XMLObject parse(String xml) {
        return parse(xml.getBytes(StandardCharsets.UTF_8));
    }

    public XMLObject parse(byte[] xml) {
        try {
            Document document = getParserPool().parse(new ByteArrayInputStream(xml));
            Element element = document.getDocumentElement();
            return getUnmarshallerFactory().getUnmarshaller(element).unmarshall(element);
        } catch (UnmarshallingException | XMLParserException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Metadata resolveMetadata(String xml, List<SimpleKey> trustedKeys) {
        XMLObject object = parse(xml);
        if (trustedKeys != null) {
            validateSignature((SignableSAMLObject) object, trustedKeys);
        }
        if (object instanceof EntityDescriptor) {

        } else {
            throw new IllegalArgumentException("Unable to cast object of class:" + object.getClass().getName() + " as metadata.");
        }

        EntityDescriptor descriptor = (EntityDescriptor) object;
        Metadata desc = new Metadata();
        desc.setCacheDurationMillis(descriptor.getCacheDuration() != null ? descriptor.getCacheDuration() : -1);
        desc.setEntityId(descriptor.getEntityID());
        desc.setId(descriptor.getID());
        desc.setValidUntil(descriptor.getValidUntil());
        desc.setProviders(getSsoProviders(descriptor));
        return desc;
    }

    protected List<? extends Provider> getSsoProviders(EntityDescriptor descriptor) {
        final List<SsoProvider> providers = new LinkedList<>();
        for (RoleDescriptor roleDescriptor : descriptor.getRoleDescriptors()) {
            providers.add(getSsoProvider(roleDescriptor));
        }
        return providers;
    }

    protected SsoProvider getSsoProvider(RoleDescriptor descriptor) {
        if (descriptor instanceof SPSSODescriptor) {
            SPSSODescriptor desc = (SPSSODescriptor) descriptor;
            ServiceProvider provider = new ServiceProvider();
            provider.setId(desc.getID());
            provider.setValidUntil(desc.getValidUntil());
            provider.setProtocolSupportEnumeration(desc.getSupportedProtocols());
            provider.setNameIDs(getNameIDs(desc.getNameIDFormats()));
            provider.setArtifactResolutionService(getEndpoints(desc.getArtifactResolutionServices()));
            provider.setSingleLogoutService(getEndpoints(desc.getSingleLogoutServices()));
            provider.setManageNameIDService(getEndpoints(desc.getManageNameIDServices()));
            provider.setAuthnRequestsSigned(desc.isAuthnRequestsSigned());
            provider.setWantAssertionsSigned(desc.getWantAssertionsSigned());
            provider.setAssertionConsumerService(getEndpoints(desc.getAssertionConsumerServices()));
            //TODO
            //provider.setAttributeConsumingService(getEndpoints(desc.getAttributeConsumingServices()));
            return provider;
        } else if (descriptor instanceof IDPSSODescriptor) {

        } else {
            return null;
        }
        throw new UnsupportedOperationException();
    }

    protected List<Endpoint> getEndpoints(List<? extends org.opensaml.saml.saml2.metadata.Endpoint> services) {
        List<Endpoint> result = new LinkedList<>();
        if (services != null) {
            services
                .stream()
                .forEach(s -> {
                             Endpoint endpoint = new Endpoint()
                                 .setBinding(Binding.fromUrn(s.getBinding()))
                                 .setLocation(s.getLocation())
                                 .setResponseLocation(s.getResponseLocation());
                             result.add(endpoint);
                             if (s instanceof IndexedEndpoint) {
                                 IndexedEndpoint idxEndpoint = (IndexedEndpoint) s;
                                 endpoint
                                     .setIndex(idxEndpoint.getIndex())
                                     .setDefault(idxEndpoint.isDefault());
                             }
                         }
                );


        }


        return result;
    }

    protected List<NameID> getNameIDs(List<NameIDFormat> nameIDFormats) {
        List<NameID> result = new LinkedList<>();
        if (nameIDFormats != null) {
            nameIDFormats.stream()
                .forEach(n -> result.add(NameID.fromUrn(n.getFormat())));
        }
        return result;
    }

    @Override
    public long toMillis(Duration duration) {
        if (isNull(duration)) {
            return -1;
        } else {
            return DOMTypeSupport.durationToLong(duration);
        }
    }

    @Override
    public Duration toDuration(long millis) {
        if (millis < 0) {
            return null;
        } else {
            return DOMTypeSupport.getDataTypeFactory().newDuration(millis);
        }
    }
}
