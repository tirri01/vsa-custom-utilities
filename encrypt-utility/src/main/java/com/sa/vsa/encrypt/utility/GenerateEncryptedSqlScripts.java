package com.sa.vsa.encrypt.utility;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.sa.common.utils.EncryptionDecryptionAES;
import com.sa.common.utils.ExpressionHelper;
import com.sa.common.utils.Json;

import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

/**
 * Hello world!
 *
 */
public class GenerateEncryptedSqlScripts
{
   public static void main(String[] args) throws FileNotFoundException, IOException, ParseException
   {
      Properties properties = new Properties();
      properties.load(new FileInputStream("./config/application.properties"));
      String feature = properties.getProperty("feature");
      String integrationFileLocation = "";
      switch (feature)
      {
         case "mobileapp":
            integrationFileLocation = "./config/googleMaps.json";
            generateMobileAppMapsEnableSqlScripts(integrationFileLocation, properties);
            break;
         case "promptengg":
            String aiprovider = properties.getProperty("ai.provider");
            if (aiprovider.equalsIgnoreCase("openai"))
            {
               integrationFileLocation = "./config/openai.json";
            }
            else if (aiprovider.equalsIgnoreCase("azureopenai"))
            {
               integrationFileLocation = "./config/azureopenai.json";
            }
            else
            {
               System.out.println("Invalid provider" + integrationFileLocation);
               return;
            }
            generatePromptEnggSqlScripts(integrationFileLocation, properties);
            break;
         default:
            System.out.println("Invalid feature: " + feature);
            return;
      }
      System.out.print("Completed, check the sql scripts in config/output.sql file");
   }

   private static void generateMobileAppMapsEnableSqlScripts(String integrationFileLocation, Properties properties)
      throws IOException, ParseException
   {
      File integrationJsonFile = new File(integrationFileLocation);
      if (!integrationJsonFile.exists())
      {
         System.out.println("Input file not found at location.Please check location " + integrationFileLocation);
         return;
      }

      JSONParser parser = new JSONParser();

      Object obj = parser.parse(new FileReader(integrationJsonFile));
      JSONObject jsonObject = (JSONObject) obj;
      Map<String, Object> replacementTokens = new HashMap<String, Object>();
      String googleMapsApiAccessKey = properties.getProperty("googleMapsApiAccessKey");
      googleMapsApiAccessKey = EncryptionDecryptionAES.encrypt(String.valueOf(googleMapsApiAccessKey));
      replacementTokens.put("googleMapsApiAccessKey", googleMapsApiAccessKey);
      replacementTokens.put("vendor", properties.getProperty("vendor"));
      String encryptedIntegrationData = ExpressionHelper.substitute(Json.serializer().toString(jsonObject), replacementTokens);

      replacementTokens.clear();
      replacementTokens.put("encryptedIntegrationData", encryptedIntegrationData);
      String updateIntegrationSql = "UPDATE vsa_tenant_configurations SET param_value = '@{encryptedIntegrationData}' WHERE param_name = 'maps.vendor.configuration';";
      updateIntegrationSql = ExpressionHelper.substitute(updateIntegrationSql, replacementTokens);

      String outputFileLocation = "./config/output.sql";

      File outputFile = new File(outputFileLocation);
      if (outputFile.exists())
      {
         outputFile.delete();
      }
      FileWriter fw = new FileWriter(outputFileLocation, true);
      BufferedWriter bw = new BufferedWriter(fw);
      bw.write(updateIntegrationSql);
      bw.newLine();

      bw.close();
   }

   private static void generatePromptEnggSqlScripts(String integrationFileLocation, Properties properties)
      throws ParseException, IOException
   {
      File integrationJsonFile = new File(integrationFileLocation);
      if (!integrationJsonFile.exists())
      {
         System.out.println("Input file not found at location.Please check location " + integrationFileLocation);
         return;
      }

      JSONParser parser = new JSONParser();

      Object obj = parser.parse(new FileReader(integrationJsonFile));
      JSONObject jsonObject = (JSONObject) obj;
      Map<String, Object> replacementTokens = new HashMap<String, Object>();
      replacementTokens.put("apiBaseUrl", properties.getProperty("apiBaseUrl"));
      replacementTokens.put("apiKey", properties.getProperty("apiKey"));

      String integrationJson = ExpressionHelper.substitute(Json.serializer().toString(jsonObject), replacementTokens);

      String encryptedIntegrationData = EncryptionDecryptionAES.encrypt(String.valueOf(integrationJson));

      String enableOpenAiSql = "UPDATE vsa_tenant_configurations SET param_value = 'true' WHERE param_name = 'openai.enabled';";
      replacementTokens.clear();
      replacementTokens.put("encryptedIntegrationData", encryptedIntegrationData);

      String updateIntegrationSql = "UPDATE vsa_tenant_configurations SET param_value = '@{encryptedIntegrationData}' WHERE param_name = 'openai.api.integration.parameters';";
      updateIntegrationSql = ExpressionHelper.substitute(updateIntegrationSql, replacementTokens);

      String invokeOpenAiFile = "./config/invokeOpenAi.json";
      Object obj2 = parser.parse(new FileReader(invokeOpenAiFile));
      JSONObject jsonObject2 = (JSONObject) obj2;
      replacementTokens.clear();
      replacementTokens.put("requestUri", properties.getProperty("requestUri"));
      String invokeOpenAIIntegrationData = ExpressionHelper.substitute(Json.serializer().toString(jsonObject2), replacementTokens);
      replacementTokens.clear();
      replacementTokens.put("invokeOpenAIIntegrationData", invokeOpenAIIntegrationData);

      String updateInvokeOpenAISql = "UPDATE vsa_connector_operations SET integration_parameters = '@{invokeOpenAIIntegrationData}'  WHERE connector_operation_identifier = 'invokeOpenAi';";
      updateInvokeOpenAISql = ExpressionHelper.substitute(updateInvokeOpenAISql, replacementTokens);

      String featureSettingsFile = "./config/openaiFeatureSettings.json";
      Object obj3 = parser.parse(new FileReader(featureSettingsFile));
      JSONObject jsonObject3 = (JSONObject) obj3;
      String featureSettingsJson = Json.serializer().toString(jsonObject3);
      replacementTokens.clear();
      replacementTokens.put("featureSettingsJson", featureSettingsJson);

      String featureSettingsSql = "UPDATE vsa_tenant_configurations SET param_value = '" + featureSettingsJson
         + "' WHERE param_name = 'openai.feature.settings';";

      String outputFileLocation = "./config/output.sql";

      File outputFile = new File(outputFileLocation);
      if (outputFile.exists())
      {
         outputFile.delete();
      }
      FileWriter fw = new FileWriter(outputFileLocation, true);
      BufferedWriter bw = new BufferedWriter(fw);
      bw.write(enableOpenAiSql);
      bw.newLine();
      bw.write(updateIntegrationSql);
      bw.newLine();
      bw.write(updateInvokeOpenAISql);
      bw.newLine();
      bw.write(featureSettingsSql);
      bw.newLine();

      bw.close();

   }
}
