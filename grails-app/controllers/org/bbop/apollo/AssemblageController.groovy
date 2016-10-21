package org.bbop.apollo

import grails.converters.JSON
import grails.transaction.Transactional
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.codehaus.groovy.grails.web.json.JSONArray
import org.codehaus.groovy.grails.web.json.JSONObject

@Transactional(readOnly = true)
class AssemblageController {

//    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def permissionService
    def preferenceService
    def projectionService
    def assemblageService
    def featureProjectionService

    @Transactional
    def list() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        User user = permissionService.getCurrentUser(inputObject)
        if (Organism.count > 0) {
            Organism currentOrganism = preferenceService.getOrganismFromPreferences(user, null, inputObject.getString(FeatureStringEnum.CLIENT_TOKEN.value))
            render assemblageService.getAssemblagesForUserAndOrganism(user, currentOrganism).sort() { a, b -> a.sequenceList <=> b.sequenceList } as JSON
        } else {
            render new JSONObject() as JSON
        }

    }

    @Transactional
    def projectFeatures(){
        JSONObject inputObject = permissionService.handleInput(request, params)
        JSONArray featuresArray = JSON.parse(inputObject.getString(FeatureStringEnum.FEATURES.value)) as JSONArray
//        println "featuresArray: ${featuresString}"
        List<String> featureList = []
        for(JSONObject featureObject in featuresArray){
            featureList.add(featureObject.getString(FeatureStringEnum.UNIQUENAME.value))
        }
        println "featuresList : ${featureList}"
        List<Feature> features = Feature.findAllByUniqueNameInList(featureList)
        println "features : ${features.name}"
        Assemblage assemblage = assemblageService.generateAssemblageForFeatureRegions(features)

        render assemblageService.convertAssemblageToJson(assemblage) as JSON
    }

    def getAssemblage() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        inputObject = featureProjectionService.expandProjectionJson(inputObject)


        User user = permissionService.currentUser
        Organism organism = preferenceService.getOrganismFromPreferences(user, inputObject.getJSONArray(FeatureStringEnum.SEQUENCE_LIST.value).toString(), inputObject.getString(FeatureStringEnum.CLIENT_TOKEN.value))

        // creates a projection based on the Assemblages and caches them
        inputObject.organism = organism.commonName
        // this generates the projection

        render inputObject as JSON
    }

    @Transactional
    def addAssemblage() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        Assemblage assemblage = assemblageService.convertJsonToAssemblage(inputObject) // this will save a new assemblage
        User user = permissionService.currentUser
        user.addToAssemblages(assemblage)
        user.save(flush: true)
        render list() as JSON
    }

    @Transactional
    def addAssemblageAndReturn() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        Assemblage assemblage = assemblageService.convertJsonToAssemblage(inputObject)
        render assemblageService.convertAssemblageToJson(assemblage) as JSON
    }

    @Transactional
    def saveAssemblage() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        Assemblage storedAssemblage = assemblageService.convertJsonToAssemblage(inputObject) // this will save a new assemblage
        render assemblageService.convertAssemblageToJson(storedAssemblage) as JSON
    }

    @Transactional
    def deleteAssemblage() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        User user = permissionService.getCurrentUser(inputObject)

        inputObject.id.each{
            assemblageService.removeAssemblageById(it,user)
        }

        render list() as JSON
    }

    def searchAssemblage(String searchQuery,String filter) {
        JSONObject inputObject = permissionService.handleInput(request, params)
        User user = permissionService.getCurrentUser(inputObject);

        ArrayList<Assemblage> assemblages = new ArrayList<Assemblage>();
        Organism currentOrganism = preferenceService.getOrganismFromPreferences(user, null, inputObject.getString(FeatureStringEnum.CLIENT_TOKEN.value))
        for (Assemblage assemblage : assemblageService.getAssemblagesForUserAndOrganism(user,currentOrganism)) {
            if (assemblage.sequenceList.toLowerCase().contains(searchQuery)) {
                if(filter){
                    JSONArray jsonArray = JSON.parse(assemblage.sequenceList) as JSONArray
                    Integer numberSequences = jsonArray.size()
                    Boolean leftEdge
                    Boolean rightEdge

                    leftEdge = jsonArray.getJSONObject(0).start > 0
                    rightEdge = jsonArray.getJSONObject(0).end < jsonArray.getJSONObject(0).length

                    switch (filter){
                        case "Feature":
                            if(leftEdge || rightEdge){
                                assemblages.add(assemblage);
                            }
                            break
                        case "Combined":
                            if(numberSequences>1 && !leftEdge && !rightEdge){
                                assemblages.add(assemblage);
                            }
                            break
                        case "Scaffold":
                            if(numberSequences==1 && !leftEdge && !rightEdge){
                                assemblages.add(assemblage);
                            }
                            break
                        default:
                            assemblages.add(assemblage);
                    }
                }
                else{
                    assemblages.add(assemblage);
                }
            }
        }

        if (assemblages.size() > 0) {
            render assemblages.sort() { a, b -> a.sequenceList <=> b.sequenceList } as JSON
        } else {
            render new JSONObject() as JSON
        }
    }

}
