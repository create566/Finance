/*
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
var showControllersOnly = false;
var seriesFilter = "";
var filtersOnlySampleSeries = true;

/*
 * Add header in statistics table to group metrics by category
 * format
 *
 */
function summaryTableHeader(header) {
    var newRow = header.insertRow(-1);
    newRow.className = "tablesorter-no-sort";
    var cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Requests";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 3;
    cell.innerHTML = "Executions";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 7;
    cell.innerHTML = "Response Times (ms)";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Throughput";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 2;
    cell.innerHTML = "Network (KB/sec)";
    newRow.appendChild(cell);
}

/*
 * Populates the table identified by id parameter with the specified data and
 * format
 *
 */
function createTable(table, info, formatter, defaultSorts, seriesIndex, headerCreator) {
    var tableRef = table[0];

    // Create header and populate it with data.titles array
    var header = tableRef.createTHead();

    // Call callback is available
    if(headerCreator) {
        headerCreator(header);
    }

    var newRow = header.insertRow(-1);
    for (var index = 0; index < info.titles.length; index++) {
        var cell = document.createElement('th');
        cell.innerHTML = info.titles[index];
        newRow.appendChild(cell);
    }

    var tBody;

    // Create overall body if defined
    if(info.overall){
        tBody = document.createElement('tbody');
        tBody.className = "tablesorter-no-sort";
        tableRef.appendChild(tBody);
        var newRow = tBody.insertRow(-1);
        var data = info.overall.data;
        for(var index=0;index < data.length; index++){
            var cell = newRow.insertCell(-1);
            cell.innerHTML = formatter ? formatter(index, data[index]): data[index];
        }
    }

    // Create regular body
    tBody = document.createElement('tbody');
    tableRef.appendChild(tBody);

    var regexp;
    if(seriesFilter) {
        regexp = new RegExp(seriesFilter, 'i');
    }
    // Populate body with data.items array
    for(var index=0; index < info.items.length; index++){
        var item = info.items[index];
        if((!regexp || filtersOnlySampleSeries && !info.supportsControllersDiscrimination || regexp.test(item.data[seriesIndex]))
                &&
                (!showControllersOnly || !info.supportsControllersDiscrimination || item.isController)){
            if(item.data.length > 0) {
                var newRow = tBody.insertRow(-1);
                for(var col=0; col < item.data.length; col++){
                    var cell = newRow.insertCell(-1);
                    cell.innerHTML = formatter ? formatter(col, item.data[col]) : item.data[col];
                }
            }
        }
    }

    // Add support of columns sort
    table.tablesorter({sortList : defaultSorts});
}

$(document).ready(function() {

    // Customize table sorter default options
    $.extend( $.tablesorter.defaults, {
        theme: 'blue',
        cssInfoBlock: "tablesorter-no-sort",
        widthFixed: true,
        widgets: ['zebra']
    });

    var data = {"OkPercent": 100.0, "KoPercent": 0.0};
    var dataset = [
        {
            "label" : "FAIL",
            "data" : data.KoPercent,
            "color" : "#FF6347"
        },
        {
            "label" : "PASS",
            "data" : data.OkPercent,
            "color" : "#9ACD32"
        }];
    $.plot($("#flot-requests-summary"), dataset, {
        series : {
            pie : {
                show : true,
                radius : 1,
                label : {
                    show : true,
                    radius : 3 / 4,
                    formatter : function(label, series) {
                        return '<div style="font-size:8pt;text-align:center;padding:2px;color:white;">'
                            + label
                            + '<br/>'
                            + Math.round10(series.percent, -2)
                            + '%</div>';
                    },
                    background : {
                        opacity : 0.5,
                        color : '#000'
                    }
                }
            }
        },
        legend : {
            show : true
        }
    });

    // Creates APDEX table
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [0.9941346359552165, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [1.0, 500, 1500, "01 标的列表"], "isController": false}, {"data": [1.0, 500, 1500, "02 标的详情"], "isController": false}, {"data": [1.0, 500, 1500, "登录-投资人"], "isController": false}, {"data": [1.0, 500, 1500, "登录-管理员"], "isController": false}, {"data": [0.9474944142993935, 500, 1500, "05 账户信息"], "isController": false}, {"data": [1.0, 500, 1500, "登录-借款人"], "isController": false}, {"data": [1.0, 500, 1500, "08 我的投资（投资人身份）"], "isController": false}, {"data": [1.0, 500, 1500, "04 消息列表"], "isController": false}, {"data": [1.0, 500, 1500, "09 管理端标的列表"], "isController": false}, {"data": [1.0, 500, 1500, "03 推荐标的"], "isController": false}, {"data": [1.0, 500, 1500, "06 我的合同列表"], "isController": false}, {"data": [1.0, 500, 1500, "07 交易流水"], "isController": false}]}, function(index, item){
        switch(index){
            case 0:
                item = item.toFixed(3);
                break;
            case 1:
            case 2:
                item = formatDuration(item);
                break;
        }
        return item;
    }, [[0, 0]], 3);

    // Create statistics table
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 28046, 0, 0.0, 57.354952577907824, 2, 1180, 9.0, 392.90000000000146, 475.0, 512.0, 600.9299136508753, 1898.003904659746, 236.30052572127232], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["01 标的列表", 3133, 0, 0.0, 13.241940631982137, 3, 335, 8.0, 21.0, 40.0, 94.65999999999985, 69.64698559487819, 281.01128665192067, 25.165414716899342], "isController": false}, {"data": ["02 标的详情", 3133, 0, 0.0, 9.765400574529181, 2, 286, 6.0, 16.0, 31.0, 77.65999999999985, 69.6748654538985, 26.579174925221277, 23.47444197421385], "isController": false}, {"data": ["登录-投资人", 1, 0, 0.0, 119.0, 119, 119, 119.0, 119.0, 119.0, 119.0, 8.403361344537815, 4.620207457983193, 1.8628545168067228], "isController": false}, {"data": ["登录-管理员", 1, 0, 0.0, 113.0, 113, 113, 113.0, 113.0, 113.0, 113.0, 8.849557522123893, 5.772953539823009, 1.944482853982301], "isController": false}, {"data": ["05 账户信息", 3133, 0, 0.0, 407.2326843281192, 69, 1180, 460.0, 502.0, 523.0, 667.3199999999997, 69.14283192090396, 41.09607847396937, 24.443071440788312], "isController": false}, {"data": ["登录-借款人", 1, 0, 0.0, 104.0, 104, 104, 104.0, 104.0, 104.0, 104.0, 9.615384615384617, 5.286583533653847, 2.131535456730769], "isController": false}, {"data": ["08 我的投资（投资人身份）", 3094, 0, 0.0, 12.688106011635435, 3, 219, 8.0, 22.0, 42.0, 85.20000000000073, 69.07483479192713, 123.4385944685993, 24.081753926482406], "isController": false}, {"data": ["04 消息列表", 3133, 0, 0.0, 12.016278327481652, 3, 249, 8.0, 19.0, 35.29999999999973, 92.0, 69.68881375536624, 160.51768478073492, 24.02358521059012], "isController": false}, {"data": ["09 管理端标的列表", 3094, 0, 0.0, 14.351971557853904, 3, 230, 10.0, 23.0, 41.0, 93.05000000000018, 69.09797440650334, 512.4978713960292, 51.4186098610894], "isController": false}, {"data": ["03 推荐标的", 3133, 0, 0.0, 16.21002234280241, 3, 342, 10.0, 28.0, 55.0, 111.0, 69.67641498943624, 293.17370541671295, 24.35952789669743], "isController": false}, {"data": ["06 我的合同列表", 3096, 0, 0.0, 15.163759689922486, 4, 288, 10.0, 24.0, 44.0, 97.05999999999949, 69.01009740766332, 459.7326165214096, 24.059184350132625], "isController": false}, {"data": ["07 交易流水", 3094, 0, 0.0, 13.334518422753707, 3, 361, 8.0, 23.0, 42.25, 101.05000000000018, 69.05325179663438, 75.44881422828416, 24.47883828337723], "isController": false}]}, function(index, item){
        switch(index){
            // Errors pct
            case 3:
                item = item.toFixed(2) + '%';
                break;
            // Mean
            case 4:
            // Mean
            case 7:
            // Median
            case 8:
            // Percentile 1
            case 9:
            // Percentile 2
            case 10:
            // Percentile 3
            case 11:
            // Throughput
            case 12:
            // Kbytes/s
            case 13:
            // Sent Kbytes/s
                item = item.toFixed(2);
                break;
        }
        return item;
    }, [[0, 0]], 0, summaryTableHeader);

    // Create error table
    createTable($("#errorsTable"), {"supportsControllersDiscrimination": false, "titles": ["Type of error", "Number of errors", "% in errors", "% in all samples"], "items": []}, function(index, item){
        switch(index){
            case 2:
            case 3:
                item = item.toFixed(2) + '%';
                break;
        }
        return item;
    }, [[1, 1]]);

        // Create top5 errors by sampler
    createTable($("#top5ErrorsBySamplerTable"), {"supportsControllersDiscrimination": false, "overall": {"data": ["Total", 28046, 0, "", "", "", "", "", "", "", "", "", ""], "isController": false}, "titles": ["Sample", "#Samples", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors"], "items": [{"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}]}, function(index, item){
        return item;
    }, [[0, 0]], 0);

});
