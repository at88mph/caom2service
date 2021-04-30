/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2021.                            (c) 2021.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 5 $
*
************************************************************************
*/

package ca.nrc.cadc.caom2.datalink;

import ca.nrc.cadc.auth.AuthMethod;
import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.caom2.Artifact;
import ca.nrc.cadc.caom2.CustomAxis;
import ca.nrc.cadc.caom2.Energy;
import ca.nrc.cadc.caom2.Polarization;
import ca.nrc.cadc.caom2.PolarizationState;
import ca.nrc.cadc.caom2.Position;
import ca.nrc.cadc.caom2.ProductType;
import ca.nrc.cadc.caom2.PublisherID;
import ca.nrc.cadc.caom2.ReleaseType;
import ca.nrc.cadc.caom2.Time;
import ca.nrc.cadc.caom2.artifact.resolvers.CaomArtifactResolver;
import ca.nrc.cadc.caom2.compute.CustomAxisUtil;
import ca.nrc.cadc.caom2.compute.CutoutUtil;
import ca.nrc.cadc.caom2.compute.EnergyUtil;
import ca.nrc.cadc.caom2.compute.PolarizationUtil;
import ca.nrc.cadc.caom2.compute.PositionUtil;
import ca.nrc.cadc.caom2.compute.TimeUtil;
import ca.nrc.cadc.caom2.types.Circle;
import ca.nrc.cadc.caom2.types.Polygon;
import ca.nrc.cadc.caom2ops.ArtifactQueryResult;
import ca.nrc.cadc.caom2ops.CutoutGenerator;
import ca.nrc.cadc.caom2ops.ServiceConfig;
import ca.nrc.cadc.dali.util.DoubleArrayFormat;
import ca.nrc.cadc.net.NetUtil;
import ca.nrc.cadc.net.StorageResolver;
import ca.nrc.cadc.reg.Standards;
import ca.nrc.cadc.reg.client.RegistryClient;
import ca.nrc.cadc.util.StringUtil;
import ca.nrc.cadc.wcs.exceptions.NoSuchKeywordException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import javax.security.auth.Subject;
import org.apache.log4j.Logger;
import org.opencadc.datalink.DataLink;
import org.opencadc.datalink.ServiceDescriptor;
import org.opencadc.datalink.ServiceParameter;

/**
 * Convert Artifacts to DataLinks.
 *
 * @author pdowler
 */
public class ArtifactProcessor
{
    private static final Logger log = Logger.getLogger(ArtifactProcessor.class);
    
    private static final String CONTENT_TYPE_TAR = "application/x-tar";
    
    private URI sodaID;
    private URI defaultPackageID;
    
    private boolean initConfigDone = false;
    
    // deprecated stuff for CADC service that is being replaced by SODA
    private static String CUTOUT = "cutout";
    private static URI CUTOUT_SERVICE = URI.create("ivo://cadc.nrc.ca/caom2ops");

    private final RegistryClient registryClient;
    
    private final String runID;
    private final CaomArtifactResolver artifactResolver;
    private boolean downloadOnly;

    public ArtifactProcessor(ServiceConfig dlc, String runID)
    {
        this.sodaID = dlc.getSodaID();
        this.defaultPackageID = dlc.getPkgID();
        this.runID = runID;
        this.registryClient = new RegistryClient();
        this.artifactResolver = new CaomArtifactResolver();
        artifactResolver.setRunID(runID);
    }

    /**
     * Force DataLink generation to only include file download links. This is used
     * when passing the output off to the ManifestWriter instead of creating all the
     * links and having the writer filter them.
     *
     * @param downloadOnly
     */
    public void setDownloadOnly(boolean downloadOnly)
    {
        this.downloadOnly = downloadOnly;
    }
    
    public List<DataLink> process(URI uri, ArtifactQueryResult ar)
    {
        List<DataLink> ret = new ArrayList<>(ar.getArtifacts().size());
        int numFiles = ar.getArtifacts().size();
        boolean pkgReadable = true;
        for (Artifact a : ar.getArtifacts())
        {
            DataLink.Term sem = DataLink.Term.THIS;
            if (ProductType.PREVIEW.equals(a.getProductType()))
            {
                sem = DataLink.Term.PREVIEW;
                numFiles--; // exclude from #pkg
            }
            else if (ProductType.THUMBNAIL.equals(a.getProductType()))
            {
                sem = DataLink.Term.THUMBNAIL;
                numFiles--; // exclude from #pkg
            }
            else if (ProductType.CATALOG.equals(a.getProductType()))
            {
                sem = DataLink.Term.DERIVATION;
            }
            else if (ProductType.AUXILIARY.equals(a.getProductType())
                    || ProductType.WEIGHT.equals(a.getProductType())
                    || ProductType.NOISE.equals(a.getProductType())
                    || ProductType.INFO.equals(a.getProductType()))
            {
                sem = DataLink.Term.AUXILIARY;
            }
            
            Boolean readable  = null;
            if (ReleaseType.DATA.equals(a.getReleaseType()))
                readable = ar.dataReadable;
            else if (ReleaseType.META.equals(a.getReleaseType()))
                readable = ar.metaReadable;
            // else: new releaseType is not likely without major caom design change
            
            if (readable != null) {
                pkgReadable = pkgReadable && readable;
            }
            // direct download links
            try
            {
                DataLink dl = new DataLink(uri.toASCIIString(), sem);
                dl.accessURL = getDownloadURL(a);
                dl.contentType = a.contentType;
                dl.contentLength = a.contentLength;
                dl.readable = readable;
                dl.description = "download " + a.getURI().toASCIIString();
                ret.add(dl);
            }
            catch(MalformedURLException ex)
            {
                DataLink dl = new DataLink(uri.toASCIIString(), sem);
                dl.errorMessage = "FatalFault: failed to generate download URL: " + ex.toString();
                ret.add(dl);
            }

            if (!downloadOnly && canCutout(a))
            {
                try
                {
                    ArtifactBounds ab = generateBounds(a);
                    DataLink link;
                    
                    link = new DataLink(uri.toASCIIString(), DataLink.Term.CUTOUT);
                    link.serviceDef = "soda-" + UUID.randomUUID();
                    link.contentType = a.contentType; // unchanged
                    link.contentLength = null; // unknown
                    link.readable = readable;
                    link.description = "SODA-sync cutout of " + a.getURI().toASCIIString();
                    ServiceDescriptor sds = generateServiceDescriptor(sodaID, Standards.SODA_SYNC_10, link.serviceDef, a.getURI(), ab);
                    if (sds != null) {
                        link.descriptor = sds;
                        ret.add(link);
                    }
                    
                    link = new DataLink(uri.toASCIIString(), DataLink.Term.CUTOUT);
                    link.serviceDef = "soda-" + UUID.randomUUID();
                    link.contentType = a.contentType; // unchanged
                    link.contentLength = null; // unknown
                    link.readable = readable;
                    link.description = "SODA-async cutout of " + a.getURI().toASCIIString();
                    ServiceDescriptor sda = generateServiceDescriptor(sodaID, Standards.SODA_ASYNC_10, link.serviceDef, a.getURI(), ab);
                    if (sda != null) {
                        link.descriptor = sda;
                        ret.add(link);
                    }
                }
                catch(NoSuchKeywordException ex)
                {
                    throw new RuntimeException("FAIL: invalid WCS", ex);
                }
            }
        }
        log.debug("numFiles: " + numFiles);
        if (numFiles > 1) {
            
            URL pkg = getBasePackageURL(uri);
            if (pkg != null) {
                DataLink link = new DataLink(uri.toASCIIString(), DataLink.Term.PKG);
                try {
                    link.accessURL = getPackageURL(pkg, uri);
                    link.contentType = CONTENT_TYPE_TAR;
                    link.description = "single download containing all files (previews and thumbnails excluded)";
                    link.readable = pkgReadable;
                } catch (MalformedURLException ex) {
                    link.errorMessage = "failed to create package link: " + ex;
                }
                ret.add(link);
            }
        }
        return ret;
    }
    
    private class ArtifactBounds
    {
        public String circle;
        public String poly;
        public String bandMin;
        public String bandMax;
        public String timeMin;
        public String timeMax;
        public Set<PolarizationState> pol;
        public String customParam;
        public String customMin;
        public String customMax;
    }
    
    private boolean canCutout(Artifact a) {
        StorageResolver sr = artifactResolver.getStorageResolver(a.getURI());
        if (!(sr instanceof CutoutGenerator)) {
            log.debug("canCutout: no code to generate cutout for " + a.getURI());
            return false;
        }
        
        CutoutGenerator cg = (CutoutGenerator) sr;
        if (!cg.canCutout(a)) {
            log.debug("canCutout: artifact not supported by SODA " + a.getURI());
            return false;
        }
        
        if (!CutoutUtil.canCutout(a)) {
            log.debug("canCutout: insufficient metadata for SODA to compute cutout " + a.getURI());
            return false;
        }
        
        // file type check moved into CutoutGenerator.canCutout(Artifact)
        
        return true;
    }
    
    private ArtifactBounds generateBounds(Artifact a)
        throws NoSuchKeywordException
    {
        ArtifactBounds ret = new ArtifactBounds();
        Set<Artifact> aset = new TreeSet<>();
        aset.add(a);

        // compute artifact-specific metadata for cutout params
        DoubleArrayFormat daf = new DoubleArrayFormat();

        Position pos = PositionUtil.compute(aset);
        if (pos != null) 
            log.debug("pos: " + pos.bounds + " " + pos.dimension);
        if (pos != null && pos.bounds != null && pos.bounds != null 
                && pos.dimension != null && (pos.dimension.naxis1 > 1 || pos.dimension.naxis2 > 1))
        {
            Polygon outer = (Polygon) pos.bounds;
            ret.poly = daf.format(new CoordIterator(outer.getPoints().iterator()));

            Circle msc = outer.getMinimumSpanningCircle();
            ret.circle = daf.format(new double[] { msc.getCenter().cval1, msc.getCenter().cval2, msc.getRadius() });
        }

        Energy nrg = EnergyUtil.compute(aset);
        if (nrg != null) log.debug("nrg: " + nrg.bounds + " " + nrg.dimension);
        if (nrg != null && nrg.bounds != null && nrg.dimension != null && nrg.dimension > 1)
        {
            //double[] val = new double[] { nrg.bounds.getLower(), nrg.bounds.getUpper() };
            //ret.band = daf.format(val);
            ret.bandMin = Double.toString(nrg.bounds.getLower());
            ret.bandMax = Double.toString(nrg.bounds.getUpper());
        }

        Time tim = TimeUtil.compute(aset);
        if (tim != null) log.debug("tim: " + tim.bounds + " " + tim.dimension);
        if (tim != null && tim.bounds != null && tim.dimension != null && tim.dimension > 1)
        {
            //double[] val = new double[] { tim.bounds.getLower(), tim.bounds.getUpper() };
            //ret.time = daf.format(val);
            ret.timeMin = Double.toString(tim.bounds.getLower());
            ret.timeMax = Double.toString(tim.bounds.getUpper());
        }

        Polarization pol = PolarizationUtil.compute(aset);
        if (pol != null && pol.dimension != null && pol.dimension > 1)
        {
            ret.pol = pol.states;
        }
        
        CustomAxis ca = CustomAxisUtil.compute(aset);
        if (ca != null) log.debug("custom: " + ca.getCtype() + " " + ca.bounds + " " + ca.dimension);
        if (ca != null && ca.bounds != null && ca.dimension != null && ca.dimension > 1) {
            ret.customParam = ca.getCtype();
            ret.customMin = Double.toString(ca.bounds.getLower());
            ret.customMax = Double.toString(ca.bounds.getUpper());
        }
            
        return ret;
    }
    
    private ServiceDescriptor generateServiceDescriptor(URI serviceID, URI standardID, String id, URI artifactURI, ArtifactBounds ab)
    {
        if (serviceID == null)
            return null; // no SODA support configured
        
        if (ab.poly == null && ab.bandMin == null && ab.bandMax == null
                && ab.timeMin == null && ab.timeMax == null && ab.pol == null)
            return null;

        Subject caller = AuthenticationUtil.getCurrentSubject();
        AuthMethod authMethod = AuthenticationUtil.getAuthMethod(caller);
        if (authMethod == null)
            authMethod = AuthMethod.ANON;
        
        // generate artifact-specific SODA service descriptor
        URL accessURL = registryClient.getServiceURL(serviceID, standardID, authMethod);
        ServiceDescriptor sd = new ServiceDescriptor(accessURL);
        sd.id = id;
        sd.standardID = standardID;
        sd.resourceIdentifier = serviceID;

        ServiceParameter sp;
        String val = artifactURI.toASCIIString();
        String arraysize = Integer.toString(val.length());
        sp = new ServiceParameter("ID", "char", arraysize, "meta.id;meta.dataset");
        sp.setValueRef(val, null);
        sd.getInputParams().add(sp);
        
        if (StringUtil.hasText(runID)) {
            arraysize = Integer.toString(runID.length());
            sp = new ServiceParameter("RUNID", "char", arraysize, "");
            sp.setValueRef(runID, null);
            sd.getInputParams().add(sp);
        }
        
        if (ab.poly != null)
        {
            sp = new ServiceParameter("POS", "char", "*", "obs.field");
            sd.getInputParams().add(sp);
        }

        if (ab.circle != null)
        {
            sp = new ServiceParameter("CIRCLE", "double", "3", "obs.field");
            sp.xtype = "circle";
            sp.unit = "deg";
            sp.setMinMax(null, ab.circle);
            sd.getInputParams().add(sp);
        }

        if (ab.poly != null)
        {
            sp = new ServiceParameter("POLYGON", "double", "*", "obs.field");
            sp.xtype = "polygon";
            sp.unit = "deg";
            sp.setMinMax(null, ab.poly);
            sd.getInputParams().add(sp);
        }

        if (ab.bandMin != null || ab.bandMax != null)
        {
            sp = new ServiceParameter("BAND", "double", "2", "em.wl;stat.interval");
            sp.xtype = "interval";
            sp.unit = "m";
            sp.setMinMax(ab.bandMin, ab.bandMax);
            sd.getInputParams().add(sp);
        }

        if (ab.timeMin != null || ab.timeMax != null)
        {
            sp = new ServiceParameter("TIME", "double", "2", "time;stat.interval");
            sp.xtype = "interval";
            sp.unit = "d";
            sp.setMinMax(ab.timeMin, ab.timeMax);
            sd.getInputParams().add(sp);
        }

        if (ab.pol != null)
        {
            sp = new ServiceParameter("POL", "char", "*", "phys.polarization.state");
            for (PolarizationState s : ab.pol)
            {
                sp.getOptions().add(s.getValue());
            }
            sd.getInputParams().add(sp);
        }
        if (ab.customParam != null && (ab.customMin != null || ab.customMax != null)) {
            sp = new ServiceParameter(ab.customParam, "double", "2", null);
            sp.xtype = "interval";
            sp.unit = CustomAxisUtil.getUnits(ab.customParam);
            sp.setMinMax(ab.customMin, ab.customMax);
            sd.getInputParams().add(sp);
        }

        return sd;
        
    }
    
    /**
     * Convert a URI to a URL.
     *
     * @param a 
     * @return u
     * @throws MalformedURLException
     */
    protected URL getDownloadURL(Artifact a)
        throws MalformedURLException
    {
        URL url = artifactResolver.getURL(a.getURI());

        return url;
    }
    
    /**
     * Find the package service associated with a publisherID.
     * 
     * @param id
     * @return base package service url for current auth method or null if no such service
     */
    protected URL getBasePackageURL(URI id) {
        Subject caller = AuthenticationUtil.getCurrentSubject();
        AuthMethod authMethod = AuthenticationUtil.getAuthMethod(caller);
        if (authMethod == null) {
            authMethod = AuthMethod.ANON;
        }
        
        if ("caom".equals(id.getScheme())) {
            // not resolvable: use config
            return registryClient.getServiceURL(defaultPackageID, Standards.PKG_10, authMethod);
        }
        
        // resolvable
        PublisherID pubID = new PublisherID(id);
        URI resourceID = pubID.getResourceID();
        URL ret = registryClient.getServiceURL(resourceID, Standards.PKG_10, authMethod);
        return ret;
    }
    
    private URL getPackageURL(URL pkg, URI uri) throws MalformedURLException {
        StringBuilder sb = new StringBuilder();
        sb.append(pkg.toExternalForm());
        sb.append("?ID=").append(NetUtil.encode(uri.toASCIIString()));
        if (StringUtil.hasLength(runID)) {
            sb.append("&RUNID=").append(NetUtil.encode(runID));
        }
        return new URL(sb.toString());
    }
}
